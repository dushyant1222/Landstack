package edu.iitp.landstack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Fails startup with a clear message if the private upload directory cannot be created/written. */
@Component
public class StorageStartupCheck {
    private static final Logger log = LoggerFactory.getLogger(StorageStartupCheck.class);
    private final LandStackProperties props;

    public StorageStartupCheck(LandStackProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() throws IOException {
        Path root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("Upload storage directory is not writable: " + root);
        }
        log.info("Upload storage ready (private directory, not web-served)");
    }
}
