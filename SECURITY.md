# Security Policy

Report vulnerabilities privately via **Security → Report a vulnerability** (https://github.com/roaxio/EZ-Cam-Mic/security/advisories/new). Please don't open public issues for security problems.

Only the latest release is supported.

EZ-Cam-Mic runs a server for your local network only (Wi-Fi or USB tethering); don't expose port 4747 to the internet. HTTPS uses a self-signed certificate. Login (HTTP Basic auth) is off by default, and its default credentials are `admin` / `admin123`: change them if you turn login on. The app has no analytics or telemetry and makes no outbound connections, and the web dashboard loads nothing from the internet.
