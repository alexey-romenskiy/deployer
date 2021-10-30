package codes.writeonce.deployer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public final class Utils {

    @Nonnull
    public static JsonNode readRequest(@Nonnull HttpServletRequest request) throws IOException {

        final var inputStream = request.getInputStream();
        final var charset = ofNullable(request.getCharacterEncoding()).map(Charset::forName).orElse(null);
        final ObjectMapper objectMapper = new ObjectMapper();
        return requireNonNull(charset == null
                ? objectMapper.readTree(inputStream)
                : objectMapper.readTree(new InputStreamReader(inputStream, charset)));
    }

    public static void writeResponse(@Nonnull HttpServletResponse response, @Nonnull Map<String, Object> value)
            throws IOException {

        response.setContentType("application/json; charset=utf-8");
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(response.getOutputStream(), value);
    }

    @Nullable
    public static String optionalString(@Nonnull JsonNode body, @Nonnull String name) {
        return ofNullable(body.get(name)).map(JsonNode::textValue).map(Utils::emptyToNull).orElse(null);
    }

    @Nonnull
    public static String requiredString(@Nonnull JsonNode body, @Nonnull String name) {
        return requireNonNull(emptyToNull(body.get(name).textValue()));
    }

    @Nullable
    public static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Nonnull
    public static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    public static void executeUpdateSingleRow(@Nonnull PreparedStatement s) throws SQLException {

        final int rowsCount = s.executeUpdate();
        if (rowsCount != 1) {
            throw new RuntimeException("Expected rows update count is 1 but got " + rowsCount);
        }
    }

    @Nullable
    public static Long getSingleLongOptional(@Nonnull PreparedStatement s) throws SQLException {
        try (var r = s.executeQuery()) {
            if (!r.next()) {
                throw new RuntimeException();
            }
            final long v = r.getLong(1);
            final Long aLong = r.wasNull() ? null : v;
            if (r.next()) {
                throw new RuntimeException();
            }
            return aLong;
        }
    }

    public static long getSingleLong(@Nonnull PreparedStatement s) throws SQLException {

        try (var r = s.executeQuery()) {
            if (!r.next()) {
                throw new RuntimeException();
            }
            final var value = r.getLong(1);
            if (r.next()) {
                throw new RuntimeException();
            }
            return value;
        }
    }

    public static boolean getSingleBoolean(@Nonnull PreparedStatement s) throws SQLException {

        try (var r = s.executeQuery()) {
            if (!r.next()) {
                throw new RuntimeException();
            }
            final var value = r.getBoolean(1);
            if (r.next()) {
                throw new RuntimeException();
            }
            return value;
        }
    }

    private Utils() {
        // empty
    }
}
