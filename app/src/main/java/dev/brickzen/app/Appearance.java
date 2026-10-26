package dev.brickzen.app;

/* JADX INFO: loaded from: classes.dex */
final class Appearance {
    static final int DARK = 2;
    static final int LIGHT = 1;
    static final int SYSTEM = 0;
    final int background;
    final boolean dark;
    final int panel;
    final int primaryText;

    static int normalize(int i) {
        if (i < 0 || i > DARK) {
            return 0;
        }
        return i;
    }

    static Appearance resolve(int i, boolean z) {
        return new Appearance(normalize(i) == DARK || (normalize(i) == 0 && z));
    }

    private Appearance(boolean z) {
        this.dark = z;
        this.background = z ? -15656938 : -526608;
        this.panel = z ? -14932189 : -1;
        this.primaryText = z ? -15718114 : -1;
    }

    int color(int i) {
        int i2;
        if (!this.dark) {
            return i;
        }
        int i3 = i >>> 24;
        switch (16777215 & i) {
            case 1589046:
                i2 = i3 >= 128 ? 15331821 : 0;
                break;
            case 1856325:
                i2 = 7391647;
                break;
            case 2648665:
                i2 = 9624504;
                break;
            case 8295305:
            case 8559491:
            case 9217422:
            case 10267807:
                i2 = 10597289;
                break;
            case 10536111:
                i2 = 4549459;
                break;
            case 12305852:
            case 15001823:
                i2 = 6649451;
                break;
            case 14411732:
            case 14937562:
                i2 = 2966839;
                break;
            case 14475990:
            case 15198686:
            case 15264735:
                i2 = 2833458;
                break;
            case 14476246:
                i2 = 3427390;
                break;
            case 14870234:
                i2 = 3426107;
                break;
            case 15330786:
            case 15461603:
            case 15527652:
                i2 = 2437932;
                break;
            case 15593701:
                i2 = 1647903;
                break;
            case 16250608:
                i2 = 1120278;
                break;
            default:
                return i;
        }
        return i2 | (i3 << 24);
    }
}
