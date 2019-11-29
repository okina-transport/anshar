# Anshar How-To

### A quoi ça sert?
Anshar est un serveur de collecte et de diffusion de données SIRI.

"A pub-sub hub for SIRI data. It will connect and subscribe on SIRI endpoints, and also supply external endpoints" (cf https://github.com/entur/anshar)

  

### Abonnement entrant
Un abonnement entrant permet la collecte de données SIRI par Anshar.

Anshar collecte des données SIRI (Situations, VehicleActivities, EstimatedTimetables ...) en s'abonnant à différentes sources de données externes (APIs mises à disposition par d'autres organisations).
Ces abonnements "entrant" peuvent s'effectuer en mode SUBSCRIBE OU REQUEST_RESPONSE (par protocole REST OU SOAP):
- mode SUBSCRIBE: 
    Anshar émet une requête pour souscrire un abonnement auprès d'une source externe (api). 
    Cette requête de souscription d'un abonnement entrant définit entre autres:
        -la nature des données attendues par Anshar
        -le "endpoint" de l'api Anshar à appeler pour l'envoi des données.
    
    Ainsi, Anshar reçoit pour la durée de l'abonnement exprimée, des mises à jours de données à travers des requêtes POST émises par les sources de l'abonnement vers le endpoint Anshar défini à la souscription.
     
- mode REQUEST_RESPONSE:
    Anshar va envoyer, à intervalles de temps réguliers prédéfinis dans l'abonnement, des requêtes HTTP (REST OU SOAP) vers des sources externes, qui lui renvoient en retour les mises à jour de données correspondantes.


##### Configuration des abonnements entrant
Un fichier de configuration au format .yml permet de définir les abonnements entrant d'Anshar, ie, les sources des données Siri.
Le chemin vers ce fichier est défini par la propriété "anshar.subscriptions.config.path" du fichier application.properties (fichier de configuration global d'Anshar) 

Tous les abonnements entrant définis dans ce fichier sont chargés au démarrage de l'application. 


### Abonnement sortant

Un abonnement sortant permet la diffusion par Anshar de données SIRI vers des organisations externes.

Anshar permet à des organisations externes (entreprises ou autres) de s'abonner aux flux de données Siri qu'il collecte par ses abonnements entrant (cf ci-dessus).
Ces abonnements sortant sont initiés par des serveurs tiers, clients d'anshar, par le biais de requête POST vers l'API REST de souscription d'Anshar.
La requête de souscription d'un abonnement sortant définit entre autres: 
    -la nature des données à recevoir
    -la périodicité des envois 
    -une période de validité
    -le endpoint de l'API du serveur tiers à appeler pour l'envoi des données
 
Ainsi, les clients envoient une requête de souscription à Anshar qui crée alors un nouvel abonnement sortant.
Par la suite, à chaque réception/collecte de nouvelles données Siri (par le biais de ses abonnements entrant), Anshar va sélectionner les abonnements sortant concernés par ces nouvelles données, en fonction de la nature des données reçues(dataSetId). 
Il "pushe" alors ces données vers les serveurs abonnés concernés, par le biais d'une requête POST vers le endpoint défini à la souscription de l'abonnement sortant.

 