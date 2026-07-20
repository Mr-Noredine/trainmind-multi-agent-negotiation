public class UserGlossary {
    private UserGlossary() {}

    private static final String SEP  = "─────────────────────────────────────────────────────";
    private static final String SEP2 = "═════════════════════════════════════════════════════";

    public static void print() {
        System.out.println();
        System.out.println(SEP2);
        System.out.println("  LEXIQUE — Termes utilises par les agents (experts)");
        System.out.println(SEP2);
        System.out.println();
        System.out.println("  ACWR    Rapport charge aigue / charge chronique.");
        System.out.println("          Mesure si tu travailles plus ou moins que d'habitude.");
        System.out.println("          Zone ideale : 0.8 – 1.3 | > 1.5 = risque de blessure eleve.");
        System.out.println();
        System.out.println("  RPE     Intensite percue (1 a 10).");
        System.out.println("          1 = tres facile | 6 = modere | 8 = difficile | 10 = effort maximal.");
        System.out.println();
        System.out.println("  Volume  Quantite totale de travail :");
        System.out.println("          en musculation = series x repetitions ; en course = kilometres totaux.");
        System.out.println();
        System.out.println("  VMA     Vitesse Maximale Aerobie.");
        System.out.println("          Vitesse a laquelle ton corps consomme le maximum d'oxygene.");
        System.out.println("          Sert de base pour calculer les allures de course (ex. 85% VMA = allure tempo).");
        System.out.println();
        System.out.println("  FCmax   Frequence Cardiaque Maximale.");
        System.out.println("          Rythme cardiaque maximum possible. Zones d'effort basees dessus.");
        System.out.println();
        System.out.println("  HRV     Variabilite de la Frequence Cardiaque.");
        System.out.println("          Un HRV eleve = systeme nerveux bien recupere.");
        System.out.println("          Un HRV bas = corps sous stress, besoin de repos.");
        System.out.println();
        System.out.println("  Phase de cycle :");
        System.out.println("    Accumulation  = periode de gros volume (construction de la base)");
        System.out.println("    Intensification = periode d'intensite elevee (developper la performance)");
        System.out.println("    Peaking       = pic de forme (avant competition, intensite max, volume bas)");
        System.out.println("    Deload        = semaine de recuperation planifiee (volume -40 a -50%)");
        System.out.println();
        System.out.println("  Hypertrophie  Developpement du volume musculaire (muscles plus gros).");
        System.out.println("  Force max     Capacite a soulever la charge la plus lourde possible.");
        System.out.println("  Puissance     Force appliquee rapidement (sprints, sauts, explosivite).");
        System.out.println("  Seuil lactique  Intensite au-dela de laquelle les lactates s'accumulent.");
        System.out.println("  Courbatures   Douleurs 24-48h apres l'effort — signe normal de recuperation.");
        System.out.println("  Decharge (deload)  Semaine legere planifiee pour super-compenser.");
        System.out.println();
        System.out.println(SEP);
        System.out.println("  Les deux agents experts (Agent_P et Agent_R) utilisent ces termes");
        System.out.println("  entre eux. Votre programme final est traduit en langage simple.");
        System.out.println(SEP);
        System.out.println();
    }
}
