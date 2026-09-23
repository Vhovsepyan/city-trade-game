package citytrade.engine.state;

/** Where a public project is in its life (Numbers Sheet 16). Only OPEN projects take contributions. */
public enum ProjectStatus {
    /** Drawn at setup; appears in step 2.3 of its open round. */
    UPCOMING,
    /** Takes contributions until the Round Resolution of its deadline round. */
    OPEN,
    /** Fully contributed at the deadline (step 4.3). */
    SUCCEEDED,
    /** Not fully contributed at the deadline; all contributions are lost. */
    FAILED
}
