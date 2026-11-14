package com.itriguero.homeclock;

/**
 * Traduce los códigos estables de {@code ApplicationExitInfo} a etiquetas
 * propias y a un nivel de confianza.
 *
 * No depende de Android para poder probarse en la JVM. Los códigos son parte
 * del contrato público de {@code ApplicationExitInfo} y no cambian entre
 * versiones.
 */
final class ExitReasonMapper {

    static final int REASON_UNKNOWN = 0;
    static final int REASON_EXIT_SELF = 1;
    static final int REASON_SIGNALED = 2;
    static final int REASON_LOW_MEMORY = 3;
    static final int REASON_CRASH = 4;
    static final int REASON_CRASH_NATIVE = 5;
    static final int REASON_ANR = 6;
    static final int REASON_INITIALIZATION_FAILURE = 7;
    static final int REASON_PERMISSION_CHANGE = 8;
    static final int REASON_EXCESSIVE_RESOURCE_USAGE = 9;
    static final int REASON_USER_REQUESTED = 10;
    static final int REASON_USER_STOPPED = 11;
    static final int REASON_DEPENDENCY_DIED = 12;
    static final int REASON_OTHER = 13;
    static final int REASON_FREEZER = 14;
    static final int REASON_PACKAGE_STATE_CHANGE = 15;
    static final int REASON_PACKAGE_UPDATED = 16;

    /** Señal 9 (SIGKILL), el caso habitual de una salida forzada por el sistema. */
    static final int SIGNAL_SIGKILL = 9;

    private ExitReasonMapper() {
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= 30;
    }

    /**
     * Motivo legible. La señal solo se etiqueta como {@code signal} cuando el
     * estado es SIGKILL; otras señales no permiten distinguir con fiabilidad
     * si fueron un crash, una muerte del sistema o una salida forzada.
     */
    static String cause(int reason, int status) {
        switch (reason) {
            case REASON_EXIT_SELF:
                return "other";
            case REASON_SIGNALED:
                return status == SIGNAL_SIGKILL ? "signal" : "other";
            case REASON_LOW_MEMORY:
                return "lmk";
            case REASON_CRASH:
                return "crash";
            case REASON_CRASH_NATIVE:
                return "crash_native";
            case REASON_ANR:
                return "anr";
            case REASON_INITIALIZATION_FAILURE:
            case REASON_PERMISSION_CHANGE:
            case REASON_DEPENDENCY_DIED:
            case REASON_OTHER:
            case REASON_PACKAGE_STATE_CHANGE:
                return "other";
            case REASON_EXCESSIVE_RESOURCE_USAGE:
                return "resource";
            case REASON_USER_REQUESTED:
            case REASON_USER_STOPPED:
                return "user";
            case REASON_FREEZER:
                return "frozen";
            case REASON_PACKAGE_UPDATED:
                return "update";
            case REASON_UNKNOWN:
            default:
                return "unknown";
        }
    }

    /**
     * {@code system} para códigos documentados; {@code inferred} cuando el
     * código es desconocido o la causa procede de una señal.
     */
    static String confidence(int reason) {
        if (reason == REASON_UNKNOWN || reason == REASON_SIGNALED) return "inferred";
        if (reason < REASON_EXIT_SELF || reason > REASON_PACKAGE_UPDATED) return "inferred";
        return "system";
    }
}
