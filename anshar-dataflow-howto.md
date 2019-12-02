# Anshar Dataflow How-To

Ce document a pour but d'expliquer les flux de données et workflow associés au sein d'Anshar.
Pour rappel, Anshar est un serveur de collecte et de diffusion de données SIRI.

    "A pub-sub hub for SIRI data. It will connect and subscribe on SIRI endpoints, and also supply external endpoints" (cf https://github.com/entur/anshar)

  

### Abonnement entrant
Un abonnement entrant permet la collecte de données SIRI par Anshar.

Anshar collecte des données SIRI (Situations, VehicleActivities, EstimatedTimetables ...) en s'abonnant à différentes sources de données externes (APIs mises à disposition par d'autres organisations).

Ces abonnements "entrant" peuvent s'effectuer en mode SUBSCRIBE OU REQUEST_RESPONSE (par protocole REST OU SOAP):

- mode SUBSCRIBE: 
    Anshar émet une requête pour souscrire un abonnement auprès d'une source externe (api).
  Cette requête de souscription d'un abonnement entrant définit entre autres: 
	- la nature des données attendues par Anshar
	- le "endpoint" de l'api Anshar à appeler pour l'envoi des données.
	    
Anshar reçoit ainsi par la suite, et pour la durée de l'abonnement exprimée, des mises à jours de données à travers des requêtes POST émises par les sources de l'abonnement vers le endpoint Anshar défini à la souscription.
     
- mode REQUEST_RESPONSE:
    Anshar va envoyer, à intervalles de temps réguliers prédéfinis dans l'abonnement, des requêtes HTTP (REST OU SOAP) vers des sources externes, qui lui renvoient en retour les mises à jour de données correspondantes.


##### Configuration des abonnements entrant
Un fichier de configuration au format .yml permet de définir les abonnements entrant d'Anshar, ie, les sources des données Siri.
Le chemin vers ce fichier est défini par la propriété "anshar.subscriptions.config.path" du fichier application.properties (fichier de configuration global d'Anshar) 

Tous les abonnements entrant définis dans ce fichier sont chargés au démarrage de l'application. 
Pour qu'un abonnement soit effectivement opérationnel, il doit être défini comme actif dans ce fichier de config: propriété active à true


### Abonnement sortant

Un abonnement sortant permet la diffusion par Anshar de données SIRI vers des cibles clientes externes.

Anshar permet à des organisations externes (entreprises ou autres) de s'abonner aux flux de données Siri qu'il collecte.
Ces abonnements sortant sont initiés par des serveurs tiers, clients d'anshar, par le biais de requête POST vers l'API REST de souscription d'Anshar.

La requête de souscription d'un abonnement sortant définit entre autres: 
- la nature des données à recevoir
- la périodicité des envois 
- une période de validité
- le endpoint de l'API du serveur tiers à appeler pour l'envoi des données
 
Ainsi, les clients envoient une requête de souscription à Anshar qui crée alors un nouvel abonnement sortant.
Par la suite, à chaque réception/collecte de nouvelles données Siri (par le biais de ses abonnements entrant), Anshar va sélectionner les abonnements sortant concernés par ces nouvelles données, en fonction de la nature des données reçues(dataSetId). 
Il "pushe" alors ces données vers les serveurs abonnés concernés, par le biais d'une requête POST vers le endpoint défini à la souscription de l'abonnement sortant.

##### Configuration des abonnements sortant
Les abonnements sortant ne peuvent pas être configurés au démarrage d'Anshar.
Pour s'abonner à des données diffusées par Anshar, un serveur tiers doit effectuer une requête de souscription d'abonnement vers l'api REST d'Anshar (endpoints "/subscribe" ou "/anshar/subscribe"). 
Voir classe de test **SiriSubscriptionTest** pour un exemple de requête de souscription.
 


### Collecte des données

La collecte de données Siri s'effectue par le biais d'abonnements entrant.
Ces abonnements sont gérés, comme le reste des flux de l'application, par le biais de routes (workflows) Apache Camel.


1. Configuration des abonnements entrant 

Comme évoqué précédemment, ces abonnements sont définis dans le fichier de configuration défini par la propriété "anshar.subscriptions.config.path" du fichier application.properties.
Ils sont instanciés par Spring en tant qu'objets de type **SubscriptionSetup** à travers la classe **SubscriptionConfig**.


2. Lancement de la collecte : initialisation des routes Camel correspondant aux abonnements entrant
La classe **SubscriptionInitializer** permet le chargement et l'initialisation de ces abonnements à travers la méthode createSubscriptions().
Cette méthode récupère le bean SupscriptionConfig et les SubscriptionSetup associés, puis initialise les routes Camel correspondantes dans la méthode getRouteBuilders(): 
ces routes et les traitements associés sont définis dans les classes du package **no.rutebanken.anshar.routes.siri** (ex: Siri20ToSiriRS20RequestResponse) qui correspondent aux différents modes de requêtes d'abonnement (REST/SOAP, SUBSCRIBRE/REQUEST_RESPONSE)

Camel lance alors les requêtes de collecte des données définies dans le traitement de ces routes.


 3. Traitement des réponses aux requêtes de collecte
 
 La classe **SiriHandler** prend en charge les réponses des requêtes de collecte envoyées, avec comme point d'entrée principal la méthode *handleIncomingSiri(String subscriptionId, InputStream xml)* qui prend en entrée l'identifiant de l'abonnement concerné et le flux xml de la réponse.
 
 Les données entrantes SIRI sont alors traitées au sein de la méthode *processSiriClientRequest()*:
 -les données Xml sont transformées en un objet de type **Siri** représentant l'ensemble des données reçues
 -les objets correspondant au type d'abonnement sont extraits de cet objet Siri et enregistrés par le repository correspondant.
  Par exemple, dans le cas d'un abonnement de type *SITUATION_EXCHANGE*,  des objets de type **Situation** sont extraits ce cet objet **Siri** et enregistrés dans un cache "mémoire" Hazelcast par le biais du repository de type **Situations**
  
  
  	En résumé:
  	
  	- la classe SubscriptionInitializer gère l'initialisation des routes Camel correspondant aux abonnements entrant définis dans le fichier de configuration 
  	- la classe SiriHandler gère la collecte effective des données (cf méthode processSiriClientRequest() )
  
### Diffusion des données

La diffusion de données est implémentée autour des abonnements sortant.
Comme les abonnements entrant, ceux-ci sont gérés par le biais de routes Camel.

1. Initialisation d'un abonnement sortant
Un abonnement sortant est créé lors de la réception par Anshar d'une requête POST de souscription envoyée par un serveur tiers vers un des 2 endpoint "/subscribe" ou "/anshar/subscribe" de l'api Anshar.
Cette api est définie par la classe **Siri20RequestHandlerRoute** au niveau de la méthode *configure()*.
Les demandes de souscriptions envoyées vers les endpoints de l'API REST "/subscribe" et "/anshar/subscribe" sont redirigées vers le endpoint Camel "direct:process.subscription.request".
Celui-ci est lui même le point d'entrée d'une route Camel implémentant la création et l'enregistrement des abonnements sortant: toujours dans la méthode configure() de la classe **Siri20RequestHandlerRoute** , la route définie par l'instruction *from("direct:process.subscription.request")* ) appelle la méthode *handleIncomingSiri()* de la classe **SiriHandler** évoquée précédemment.
Cet appel se poursuit sur celui vers la méthode *processSiriServerRequest()* qui passe le relais à la classe **ServerSubscriptionManager**  à travers la méthode *handleSubscriptionRequest()*, qui va créer l'abonnement sortant (instanciation objet  **OutboundSubscriptionSetup**) et le mettre en cache (cf méthode *addSubscription()*)


2. Diffusion des données
Lorsque des données Siri, correspondant à un abonnement entrant précédemment défini, sont collectées (cf "Collecte des données"), elles sont envoyées vers les cibles définies par les abonnements sortant "matchant" le type de données reçues.
Cette diffusion est initiée depuis la classe **SiriHandler**, au niveau de la méthode *processSiriClientRequest()* responsable de la collecte des données, par l'appel à la méthode *pushUpdatesAsync()* de la classe **ServerSubscriptionManager**.
L'envoi effectif des données est géré  par des routes Camel définies au niveau de la méthode *pushSiriData()* (de la classe **CamelRouteManager**) à travers l'instanciation d'objets de type **SiriPushRouteBuilder**


		En résumé

		- la classe Siri20RequestHandlerRoute gère la définition de l'api REST d'Anshar ainsi que les routes Camel implémentant les traitements correspondant
		- la classe ServerSubscriptionManager gère la diffusion effective des données (cf méthode pushUpdatesAsync())


### Persistance des données


Les données SIRI collectées (Situations, Vehicle Activities, Estimated Timetables, etc ...) sont persistées dans un cache mémoire distribué implémenté par la librairie Hazelcast.

L'accès à ce cache se fait par le biais des classe repository du package **no.rutebanken.anshar.data**. Par exemple, la classe **Situations** est le repository qui permet de gérer les objets de type **PtSituationElement** correspondant à des situations (au sens SIRI). 
Ces repositories sont utilisées par la classe **SiriHandler** pour effectuer la mise en cache des données reçues (cf, par exemple, appel à la méthode *addAll()* de la classe **Situations**). 

Ce cache permet, au moment de la collecte, de distinguer les nouvelles données des données déjà reçues précédemment, et ainsi, au moment de la diffusion, de ne réaliser un "push" vers les cibles d'abonnements sortant que des nouvelles données.

##### Implémentation/Configuration du cache mémoire

Le cache mémoire d'Anshar implémenté par Hazelcast est prévu pour fontionner par défaut sur un cluster de noeuds gérés par un service Kurbenetes, de manière à être distribué sur plusieurs VMs/Containers.
Ce cache est implémenté à travers le service **ExtendedHazelcastService** auquel est injecté le service **ExtendedKubernetesService** qui prend en charge la gestion du cluster de noeuds. 

Concrètement, au niveau des classes repository, exemple objet **Situations**, la communication avec le cache Hazelcast se fait à travers des maps de données, gérées par Hazelcast (type **IMap** ou **ReplicatedMap**)  et injectées en tant que propriétés à l'instanciation du repository.
Pour certains repositories, le service hazelcastSercice de type **ExtendedHazelcastService** est également injecté.


    Pour exécuter Anshar en local, sans cluster Kubernetes, il faut désactiver le mode Kubernetes dans le fichier de configuration application.properties: définir la propriété  **rutebanken.kubernetes.enabled** à false.













 
 
 
 


  



 