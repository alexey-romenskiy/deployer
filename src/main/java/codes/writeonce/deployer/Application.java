package codes.writeonce.deployer;

import org.postgresql.ds.PGSimpleDataSource;

import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static java.text.MessageFormat.format;
import static java.util.Objects.requireNonNull;

public class Application {

    @Nonnull
    private final DataSource dataSource;

    @Nonnull
    public final String canRegisterArtifactSql;

    @Nonnull
    public final String selectSequenceForUpdateSql;

    @Nonnull
    public final String selectUserIdForUpdateSql;

    @Nonnull
    public final String updateSequenceSql;

    @Nonnull
    public final String updateUserActivitySql;

    @Nonnull
    public final String registerArtifactSql;

    @Nonnull
    public final String createUserSql;

    public static synchronized Application get(ServletContext servletContext) throws IOException {

        var application = (Application) servletContext.getAttribute(Application.class.getName());
        if (application == null) {
            application = new Application();
            servletContext.setAttribute(Application.class.getName(), application);
        }
        return application;
    }

    public Application() throws IOException {

        final var configPath = Paths.get(System.getProperty("user.home"), ".deployer");
        final var properties = new Properties();
        try (var in = Files.newInputStream(configPath)) {
            properties.load(in);
        }

        final var schemaName = requireNonNull(properties.getProperty("db.schema"));

        final PGSimpleDataSource dataSource = new PGSimpleDataSource();

        dataSource.setServerName(requireNonNull(properties.getProperty("db.host")));
        dataSource.setPortNumber(Integer.parseInt(requireNonNull(properties.getProperty("db.port"))));
        dataSource.setDatabaseName(requireNonNull(properties.getProperty("db.name")));
        dataSource.setCurrentSchema(schemaName);
        dataSource.setUser(requireNonNull(properties.getProperty("db.user")));
        dataSource.setPassword(requireNonNull(properties.getProperty("db.password")));

        this.dataSource = dataSource;

        canRegisterArtifactSql = format("select can_register_artifact from {0}.user where id = ?", schemaName);

        selectSequenceForUpdateSql = format("select last_id from {0}.sequence where name = ? for update", schemaName);

        selectUserIdForUpdateSql = format("select id from {0}.user where username = ? for update", schemaName);

        updateSequenceSql = format("update {0}.sequence set last_id = ? where name = ?", schemaName);

        updateUserActivitySql = format("update {0}.user set last_activity_time = ? where id = ?", schemaName);

        registerArtifactSql = format("insert into {0}.registered_artifact (" +
                                     "id, " +
                                     "group_id, " +
                                     "artifact_id, " +
                                     "classifier, " +
                                     "extension, " +
                                     "base_version, " +
                                     "version, " +
                                     "snapshot, " +
                                     "url, " +
                                     "build_vcs_number, " +
                                     "teamcity_build_id, " +
                                     "teamcity_build_conf_name, " +
                                     "teamcity_project_name, " +
                                     "user_id, " +
                                     "registration_time " +
                                     ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", schemaName);

        createUserSql = format("insert into {0}.user (" +
                               "id, " +
                               "username, " +
                               "last_activity_time, " +
                               "can_login, " +
                               "can_register_artifact, " +
                               "can_request_deployment " +
                               ") values (?, ?, ?, ?, ?, ?)", schemaName);
    }

    @Nonnull
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        // empty
    }
}
