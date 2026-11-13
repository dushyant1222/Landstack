package edu.iitp.landstack.users;

import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.common.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account bootstrap.
 * <ul>
 *   <li>Production: if the user table is empty and LANDSTACK_BOOTSTRAP_ADMIN_EMAIL/PASSWORD are set,
 *       create that single administrator. Nothing is created otherwise; there are no built-in credentials.</li>
 *   <li>Dev profile only ({@code landstack.seed.demo-users=true}): create three clearly-labelled demo accounts
 *       using LANDSTACK_DEMO_PASSWORD. Skipped (with a warning) if that variable is not set.</li>
 * </ul>
 * Passwords are never logged.
 */
@Component
public class AccountBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AccountBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final LandStackProperties props;

    public AccountBootstrap(AppUserRepository users, PasswordEncoder encoder, LandStackProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var b = props.bootstrap();
        if (users.count() == 0 && !Text.isBlank(b.adminEmail()) && !Text.isBlank(b.adminPassword())) {
            PasswordPolicy.check("LANDSTACK_BOOTSTRAP_ADMIN_PASSWORD", b.adminPassword());
            users.save(new AppUser(b.adminEmail(), "Administrator", encoder.encode(b.adminPassword()), Role.ADMINISTRATOR));
            log.info("Bootstrap administrator account created");
        }
        if (props.seed().demoUsers()) {
            String pw = props.seed().demoPassword();
            if (Text.isBlank(pw)) {
                log.warn("Demo seeding enabled but LANDSTACK_DEMO_PASSWORD is not set; demo accounts were NOT created");
                return;
            }
            PasswordPolicy.check("LANDSTACK_DEMO_PASSWORD", pw);
            seed("admin@landstack.demo", "Demo Admin A", Role.ADMINISTRATOR, pw);
            seed("operator@landstack.demo", "Demo Operator B", Role.DATA_OPERATOR, pw);
            seed("reviewer@landstack.demo", "Demo Reviewer C", Role.READ_ONLY_REVIEWER, pw);
        }
    }

    private void seed(String email, String name, Role role, String pw) {
        if (!users.existsByEmail(email)) {
            users.save(new AppUser(email, name, encoder.encode(pw), role));
            log.info("Demo account created for role {} (dev profile only)", role);
        }
    }
}
