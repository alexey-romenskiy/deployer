package codes.writeonce.deployer;

import com.fasterxml.jackson.databind.JsonNode;
import org.postgresql.util.PSQLException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;

import static codes.writeonce.deployer.Utils.executeUpdateSingleRow;
import static codes.writeonce.deployer.Utils.getSingleBoolean;
import static codes.writeonce.deployer.Utils.getSingleLong;
import static codes.writeonce.deployer.Utils.getSingleLongOptional;
import static codes.writeonce.deployer.Utils.nullToEmpty;
import static codes.writeonce.deployer.Utils.optionalString;
import static codes.writeonce.deployer.Utils.readRequest;
import static codes.writeonce.deployer.Utils.requiredString;
import static codes.writeonce.deployer.Utils.writeResponse;
import static java.sql.Connection.TRANSACTION_SERIALIZABLE;
import static java.util.Objects.requireNonNull;

public class TrackServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 4488136595282527138L;

    private Application application;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            application = Application.get(config.getServletContext());
        } catch (IOException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        try {
            final var now = Instant.now();
            final var username = requireNonNull(request.getHeader("X-Remote-User"));
            final JsonNode body = readRequest(request);

            while (true) {
                try (var c = application.getConnection()) {
                    c.setTransactionIsolation(TRANSACTION_SERIALIZABLE);
                    boolean commit = false;
                    try {
                        final long userId = getUserId(c, username, now);
                        if (canRegisterArtifact(c, userId)) {
                            final var teamcity = body.get("teamcity");
                            registerArtifact(
                                    c,
                                    now,
                                    userId,
                                    requiredString(body, "groupId"),
                                    requiredString(body, "artifactId"),
                                    optionalString(body, "classifier"),
                                    requiredString(body, "extension"),
                                    requiredString(body, "baseVersion"),
                                    requiredString(body, "version"),
                                    body.get("snapshot").booleanValue(),
                                    requiredString(body, "url"),
                                    teamcity == null ? null : optionalString(teamcity, "buildVcsNumber"),
                                    teamcity == null ? null : optionalString(teamcity, "teamcityBuildId"),
                                    teamcity == null ? null : optionalString(teamcity, "teamcityBuildConfName"),
                                    teamcity == null ? null : optionalString(teamcity, "teamcityProjectName")
                            );
                            writeResponse(response, Collections.emptyMap());
                            commit = true;
                        } else {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        }
                        break;
                    } finally {
                        if (commit) {
                            c.commit();
                        } else {
                            c.rollback();
                        }
                    }
                } catch (PSQLException e) {
                    if (e.getErrorCode() != 40001) {
                        throw e;
                    }
                }
            }
        } catch (Throwable e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void registerArtifact(
            @Nonnull Connection c,
            @Nonnull Instant now,
            long userId,
            @Nonnull String groupId,
            @Nonnull String artifactId,
            @Nullable String classifier,
            @Nonnull String extension,
            @Nonnull String baseVersion,
            @Nonnull String version,
            boolean snapshot,
            @Nonnull String url,
            @Nullable String buildVcsNumber,
            @Nullable String teamcityBuildId,
            @Nullable String teamcityBuildConfName,
            @Nullable String teamcityProjectName
    ) throws SQLException {

        try (var s = c.prepareStatement(application.registerArtifactSql)) {
            int i = 0;
            s.setLong(++i, getNextId(c, "registered_artifact"));
            s.setString(++i, groupId);
            s.setString(++i, artifactId);
            s.setString(++i, nullToEmpty(classifier));
            s.setString(++i, extension);
            s.setString(++i, baseVersion);
            s.setString(++i, version);
            s.setBoolean(++i, snapshot);
            s.setString(++i, url);
            s.setString(++i, buildVcsNumber);
            s.setString(++i, teamcityBuildId);
            s.setString(++i, teamcityBuildConfName);
            s.setString(++i, teamcityProjectName);
            s.setLong(++i, userId);
            s.setTimestamp(++i, Timestamp.from(now));
            executeUpdateSingleRow(s);
        }
    }

    private boolean canRegisterArtifact(@Nonnull Connection c, long userId) throws SQLException {

        try (var s = c.prepareStatement(application.canRegisterArtifactSql)) {
            s.setLong(1, userId);
            return getSingleBoolean(s);
        }
    }

    private long getUserId(@Nonnull Connection c, @Nonnull String username, @Nonnull Instant now) throws SQLException {

        final long userId;
        try (var s = c.prepareStatement(application.selectUserIdForUpdateSql)) {
            s.setString(1, username);
            final var optionalUserId = getSingleLongOptional(s);
            if (optionalUserId == null) {
                userId = createUser(c, username, now);
            } else {
                userId = optionalUserId;
                touchUser(c, userId, now);
            }
        }
        return userId;
    }

    private void touchUser(@Nonnull Connection c, long userId, @Nonnull Instant now) throws SQLException {

        try (var s = c.prepareStatement(application.updateUserActivitySql)) {
            s.setTimestamp(1, Timestamp.from(now));
            s.setLong(2, userId);
            executeUpdateSingleRow(s);
        }
    }

    private long createUser(@Nonnull Connection c, @Nonnull String username, @Nonnull Instant now) throws SQLException {

        final long userId = getNextId(c, "user");
        try (var s = c.prepareStatement(application.createUserSql)) {
            int i = 0;
            s.setLong(++i, userId);
            s.setString(++i, username);
            s.setTimestamp(++i, Timestamp.from(now));
            s.setBoolean(++i, false);
            s.setBoolean(++i, false);
            s.setBoolean(++i, false);
            executeUpdateSingleRow(s);
        }
        return userId;
    }

    private long getNextId(@Nonnull Connection c, @Nonnull String name) throws SQLException {

        final long nextId;
        try (var s = c.prepareStatement(application.selectSequenceForUpdateSql)) {
            s.setString(1, name);
            nextId = getSingleLong(s) + 1;
        }
        try (var s = c.prepareStatement(application.updateSequenceSql)) {
            s.setLong(1, nextId);
            s.setString(2, name);
            executeUpdateSingleRow(s);
        }
        return nextId;
    }
}
