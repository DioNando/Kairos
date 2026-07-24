# Règles R8 / ProGuard de Kairós (minification + shrinkResources activés en release).
#
# Room et DataStore embarquent leurs propres « consumer rules » : on n'ajoute ici que les
# garde-fous propres à l'app, là où R8 ne peut pas deviner qu'un symbole est utilisé par
# réflexion ou par nom.

# --- Enums persistés par Room (TypeConverters) ----------------------------------------------------
# Quadrant est stocké via son `name` puis relu par `valueOf`. On conserve values()/valueOf()
# pour éviter tout dépouillement qui casserait la (dé)sérialisation.
-keepclassmembers enum com.kairos.app.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
