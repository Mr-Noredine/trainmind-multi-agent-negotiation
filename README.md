# TrainMind

TrainMind est un système multi-agent JADE qui recommande une séance sportive du jour avec des décisions issues de la plateforme rAison.

La version finale couvre trois objectifs explicites :

- `course` / `running` ;
- `force` / `strength` ;
- `masse` / `hypertrophy`.

L'objectif hybride a été retiré pour garder un périmètre déterministe pendant la démonstration.

Le système fait dialoguer deux agents experts :

- `Agent_P` : agent Performance, il propose une séance utile pour progresser ;
- `Agent_R` : agent Recovery, il analyse le risque et demande des garanties ;
- `Agent_Manager` : il coordonne la session ;
- `Agent_User` : il collecte le contexte et affiche le programme final.

Les choix de séance et l'évaluation Recovery sont portés par deux projets rAison :

- `PRJ31575` — TrainMind_Performance ;
- `PRJ31625` — TrainMind_Recovery.

Java orchestre les agents JADE, les messages FIPA ACL et la boucle de négociation. Il traduit les options rAison en programme lisible et applique les garanties de sécurité négociées.

## Lancer le projet

```bash
git clone https://github.com/Mr-Noredine/trainmind-multi-agent-negotiation.git
cd trainmind-multi-agent-negotiation
cp .env.example .env   # puis renseigner la clé API rAison
./run.sh               # compile dans bin/ et démarre JADE avec les quatre agents
```

Sans clé rAison, le code compile mais les agents ne peuvent pas négocier :
les décisions viennent de l'API rAison. Pour vérifier seulement la compilation :

```bash
javac -cp JADE-all-4.6.0/JADE-bin-4.6.0/jade/lib/jade.jar -d bin $(find src/main/java -name '*.java')
```

Compilé avec OpenJDK 21. JADE 4.6.0 est fourni dans le dépôt, rien d'autre à installer.

## Prérequis

- un JDK avec `javac` et `java` disponibles (compilé avec OpenJDK 21) ;
- `bash` pour exécuter `run.sh` ;
- un accès réseau à l'API rAison ;
- une clé API rAison valide pour les deux projets.

La distribution JADE utilisée par le script est fournie dans `JADE-all-4.6.0/`.

## Structure

```text
trainmind-multi-agent-negotiation/
├── README.md
├── run.sh
├── .env
└── src/main
    ├── data
    │   ├── athlete_profile.properties
    │   └── training_history.csv
    └── java
        ├── agents
        ├── api
        ├── behaviours
        └── model
```

Les classes restent dans le package Java par défaut pour garder le lancement JADE simple :

```text
agent_manager:Agent_Manager;agent_r:Agent_R;agent_p:Agent_P;agent_user:Agent_User
```

## Configuration rAison

Le fichier `.env` doit contenir la configuration rAison. Il ne doit pas être committé.

Variables minimales :

```text
RAISON_MODE=raison
RAISON_BASE_URL=https://api.ai-raison.com
RAISON_PERFORMANCE_PROJECT_ID=PRJ31575
RAISON_RECOVERY_PROJECT_ID=PRJ31625
RAISON_API_KEY=<cle_api>
```

Si les deux projets rAison utilisent deux clés différentes, elles peuvent être précisées séparément :

```text
RAISON_PERFORMANCE_API_KEY=<cle_performance>
RAISON_RECOVERY_API_KEY=<cle_recovery>
```

Le fichier peut aussi contenir les IDs `OPT...` des options et éléments rAison utilisés par Java.

Cette version utilise rAison comme moteur de décision. Java conserve uniquement
les règles de protocole, les conversions de valeurs et un repli conservateur si
Recovery ne couvre pas encore un contexte.

## Lancement

Depuis la racine du dépôt :

```bash
./run.sh
```

Le script :

1. recherche les fichiers `src/main/java/**/*.java` ;
2. compile dans `bin/` ;
3. lance JADE avec les quatre agents.

Au démarrage, l'application propose deux modes de saisie :

- répondre aux questions dans le terminal ;
- coller directement un contexte avancé `key=value` à la première question.

## Données utilisateur

`src/main/data/athlete_profile.properties` contient le profil stable de l'athlète :

```text
training_age
sessions_per_week
equipment
injuries
preferences
```

`src/main/data/training_history.csv` contient l'historique d'entraînement :

```text
date,target,category,exercise,load_kg,reps,rpe,distance_km
```

Le contexte envoyé à rAison est construit à partir de :

```text
profil stable + historique calculé + contexte du jour saisi par l'utilisateur
```

Pour la course, l'historique permet notamment de transformer une classe rAison de distance en kilomètres cohérents avec le kilométrage récent et le volume négocié.

## Négociation

La négociation utilise un Contract Net itératif :

- `cfp` : Performance soumet une proposition à Recovery ;
- `propose` : Recovery renvoie son évaluation et les garanties demandées ;
- Performance affine le plan tant que Recovery ne l'a pas validé ;
- `accept-proposal` : Performance sélectionne exactement le plan validé ;
- `inform` : Recovery confirme l'accord final au terme de la transaction ;
- `failure` : un agent signale une impossibilité fiable.

Chaque réponse est corrélée par `conversation-id` et `in-reply-to`. Recovery
confirme explicitement l'accord final avant sa transmission au Manager. Une
absence de réponse expire après 60 secondes et le dernier tour ne peut jamais
transformer automatiquement un refus en accord.

Les garanties typiques sont :

- volume réduit ;
- RPE plafonné ;
- distance validée ;
- règle d'arrêt ;
- zone douloureuse évitée.

Le nombre de tours n'est pas fixé pour tous les contextes. Un contexte critique peut conduire rapidement à une réponse de sécurité ; un contexte nominal peut montrer plusieurs tours pour intégrer les garanties demandées par Recovery.

## Validation

Compilation et lancement interactif :

```bash
./run.sh
```

Scénario de test conseillé :

```text
goal=running, cycle_phase=intensification, training_age=advanced, sleep_hours=7.5, fatigue=4, soreness=3, soreness_zone=none, joint_pain=0, joint_pain_zone=none, hrv=normal, acwr=1.0, chronic_km=22, last_push_days=3, last_pull_days=4, last_legs_days=5, available_minutes=45
```

Résultat attendu : proposition running, négociation entre Performance et Recovery, plan corrigé pendant les tours, puis programme final validé.

## Livrables associés

Le dossier contient aussi :

- `rapport_TrainMind.pdf` : rapport final ;
- `presentation_TrainMind.pdf` : présentation courte de soutenance.

## Limite importante

Si rAison ne retourne aucune option fiable pour un contexte, Java ne doit pas inventer une séance métier arbitraire. Ce cas signale un scénario rAison manquant ou un contexte non couvert.

## Sécurité

Ne committez jamais `.env` ni une clé API. Le dépôt fournit uniquement
`.env.example`. Avant toute publication, vérifiez aussi que les PDF et les
données d'exemple ne contiennent aucune information personnelle.
