package codes.writeonce.deployer;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ApplicationListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            Application.get(sce.getServletContext());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            Application.get(sce.getServletContext()).close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
