<html>
<head>
    <title>Anshar statistics</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script>
        function administerSubscription(operation,id) {

            var uri = "?operation=" + operation + "&subscriptionId="+id;
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', uri, true);
            xhr.onreadystatechange = function() {
                window.location.reload();
            };
            xhr.send(null);
        }
    </script>
</head>
<body>
<div class="jumbotron text-center">
    <h2>Anshar status/statistics</h2>
    <small>Server started ${body.serverStarted}</small>
    <small>(${body.secondsSinceDataReceived}s)</small>
    <br /><small>- ${body.environment} -</small>
</div>
<div class="container">

    <ul class="nav nav-tabs" id="tabs" role="tablist">
        <li class="nav-item">
            <a class="nav-link active" id="inbound-tab" data-toggle="tab" href="#inbound" onclick="location.hash='inbound'" role="tab" aria-controls="inbound" aria-selected="true">Inbound <span class="badge alert-success"></span> <span class="glyphicon glyphicon-arrow-down"></span> </a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="outbound-tab" data-toggle="tab" href="#outbound" onclick="location.hash='outbound'" role="tab" aria-controls="outbound" aria-selected="false">Outbound <span class="badge alert-success">${body.outbound?size}</span> <span class="glyphicon glyphicon-arrow-up"></span></a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="polling-tab" data-toggle="tab" href="#polling" onclick="location.hash='polling'" role="tab" aria-controls="polling" aria-selected="false">Polling clients <span class="glyphicon glyphicon-arrow-down"></span><span class="glyphicon glyphicon-arrow-up"></span></a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="distribution-tab" data-toggle="tab" href="#distribution" onclick="location.hash='distribution'" role="tab" aria-controls="distribution" aria-selected="false">Distribution <span class="glyphicon glyphicon-equalizer"></span></a>
        </li>
        <li class="nav-item text-right">
            <a class="nav-link" id="admin-tab" data-toggle="tab" href="#admin" onclick="location.hash='admin'" role="tab" aria-controls="admin" aria-selected="false">Admin <span class="glyphicon glyphicon-wrench"></span></a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="map-tab" data-toggle="tab" href="#map" onclick="location.hash='map'" role="tab" aria-controls="map" aria-selected="false">Vehicles <span class="glyphicon glyphicon-globe"></span></a>
        </li>
    </ul>

    <div class="tab-content">
        <div class="tab-pane" id="inbound" role="tabpanel" aria-labelledby="inbound-tab">

            <#list body.types as type>
                <table class="table">
                <thead>
                    <tr><th colspan="9"><h4>${type.typeName}</h4></th></tr>

                    <tr>
                        <th>#</th>
                        <th>Started</th>
                        <th>Ok</th>
                        <th class="col-md-2">Activated</th>
                        <th class="col-md-3">Vendor</th>
                        <th class="col-md-2">Last data received</th>
                        <th>Requests<br />processed</th>
                        <th>Objects<br />processed</th>
                        <th>Data<br />processed</th>
                    </tr>
                </thead>
                <tbody>
                    <#list type.subscriptions?sort_by("vendor") as item>
                        <tr data-toggle="collapse" data-target="#accordion${type?counter}-${item?counter}" style="cursor: pointer" class="clickable ${item.healthy???then(item.healthy?then("success","danger"), "warning")}">
                            <th style="vertical-align: middle">${item?counter}</th>
                            <td align="center" style="vertical-align: middle; font-size: larger">
                                <#if item.status=='active'>
                                    <span class="glyphicon glyphicon-ok-sign text-success"></span>
                                <#else>
                                    <span class="glyphicon glyphicon-minus-sign text-warning"></span>
                                </#if>
                            </td>
                            <td align="center" style="vertical-align: middle; font-size: larger">
                                <#if item.healthy??>
                                    <#if item.healthy>
                                        <span class="glyphicon glyphicon-ok-sign text-success"></span>
                                    <#else>
                                        <span class="glyphicon glyphicon-remove-sign text-danger"></span>
                                    </#if>
                                </#if>
                            </td>
                            <td style="vertical-align: middle">${item.activated!""}</td>
                            <td style="vertical-align: middle">${item.name}</td>
                            <td style="vertical-align: middle">${item.lastDataReceived!""} ${item.flagAsNotReceivingData?then("<span class=\"glyphicon glyphicon-alert text-warning\" title=\"Subscription is alive, but not receiving data\"></span>","")}</td>
                            <td align="right" style="vertical-align: middle">${item.hitcount!0}</td>
                            <td align="right" style="vertical-align: middle">${item.objectcount!0}</td>
                            <td align="right" style="vertical-align: middle">${item.bytecountLabel!""}</td>
                        </tr>
                        <tr id="accordion${type?counter}-${item?counter}" class="collapse ${item.healthy???then(item.healthy?then("success","danger"), "warning")}">
                        <td colspan="9">
                            <table class="table table-striped">
                                <tr><th>Dataset ID</th><td><a href="validation/${item.datasetId}" target="_blank">${item.datasetId}</a></td></tr>
                                <tr><th>Vendor ID</th><td>${item.vendor}</td></tr>
                                <tr><th>Servicetype</th><td>${item.serviceType}</td></tr>
                                <tr><th>Inbound URL</th><td>${item.inboundUrl}</td></tr>
                                <tr><th>Content-Type</th><td>${item.contentType}</td></tr>
                                <tr><th>Heartbeat interval</th><td>${item.heartbeatInterval}</td></tr>
                                <tr><th>Preview interval</th><td>${item.previewInterval}</td></tr>
                                <tr><th>Change before updates</th><td>${item.changeBeforeUpdates}</td></tr>
                                <tr><th>Incremental updates</th><td>${item.incrementalUpdates}</td></tr>
                                <tr><th>Update interval</th><td>${item.updateInterval}</td></tr>
                                <tr><th>Duration</th><td>${item.durationOfSubscription}</td></tr>
                                <tr><th>Restart time</th><td>${item.restartTime!""}</td></tr>
                                <tr><th>Type</th><td>${item.subscriptionType}</td></tr>
                                <tr><th>Id</th><td>${item.subscriptionId}</td></tr>
                                <tr><th>RequestorRef</th><td>${item.requestorRef}</td></tr>
                                <tr><th>Mode</th><td>${item.subscriptionMode}</td></tr>
                                <tr>
                                    <th>URLs</th>
                                    <td>
                                        <table width="80%">
                                        <#list item.urllist?keys as label>
                                           <tr><td width="50%">${label}</td><td>${item.urllist[label]}</td></tr>
                                        </#list>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                            <table border="0" width="100%">
                                <tr>
                                    <td>
                                        <#if item.status=='deactivated'>
                                            <button type="button" class="btn btn-danger" disabled onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                                            <button type="button" class="btn btn-success" onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                                        <#else >
                                            <button type="button" class="btn btn-danger"  onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                                            <button type="button" class="btn btn-success" disabled onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                                        </#if>
                                    </td>
                                    <td>
                                        <div align="right">
                                            <span style="cursor: pointer"  class="glyphicon glyphicon-trash" onclick="administerSubscription('flush', '${item.subscriptionId}')"></span>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        </tr>
                    </#list>

                </tbody>
                </table>
            </#list>
        </div>

        <div class="tab-pane" id="outbound" role="tabpanel" aria-labelledby="outbound-tab">

                <table class="table table-striped">
                    <thead>
                    <tr>
                        <th>#</th>
                        <th>SubscriptionRef</th>
                        <th>Address</th>
                        <th>DatasetId</th>
                        <th>ClientName</th>
                        <th>Type</th>
                        <th>Heartbeat</th>
                        <th>Activated</th>
                        <th>Terminationtime</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <#list body.outbound?sort_by("subscriptionRef") as item>
                    <tr>
                        <th>${item?counter}</th>
                        <td>${item.subscriptionRef}</td>
                        <td><a href="${item.address}">URL</a></td>
                        <td>${item.datasetId}</td>
                        <td>${item.clientTrackingName}</td>
                        <td>${item.subscriptionType}</td>
                        <td>${item.heartbeatInterval}</td>
                        <td>${item.requestReceived}</td>
                        <td>${item.initialTerminationTime}</td>
                        <td>
                            <span style="cursor: pointer"  class="glyphicon glyphicon-trash" onclick="administerSubscription('terminate', '${item.subscriptionRef}')"></span>
                        </td>
                    </tr>
                    </#list>
                    </tbody>
                </table>

        </div>
        <div class="tab-pane" id="polling" role="tabpanel" aria-labelledby="polling-tab">

            <#list body.polling as pollingClient>
            <table class="table">
            <thead>
                <tr><th colspan="8"><h4>${pollingClient.typeName}</h4></th></tr>

                <tr>
                    <th class="col-md-1">#</th>
                    <th class="col-md-4">Id</th>
                    <th class="col-md-2">ClientName</th>
                    <th class="col-md-1">DatasetId</th>
                    <th class="col-md-2">Last requests</th>
                    <th class="col-md-1">Requests per min</th>
                    <th class="col-md-1 text-right">Objects remaining</th>
                </tr>
            </thead>
            <tbody>
                <#list pollingClient.polling?sort_by("id") as item>
                <tr class="success">
                    <th>${item?counter}</th>
                    <td><span title="First request: ${item.firstRequest}
Request count: ${item.requestCount}">${item.id}</span></td>
                    <td>${item.clientTrackingName}</td>
                    <td>${item.datasetId}</td>
                    <td>
                        <span title="<#list item.lastRequests as timestamp>
- ${timestamp}</#list>">${item.lastRequests[0]}</span>
                    </td>
                    <td>${item.requestsPerMinute}</td>
                    <td class="text-right">${item.count}</td>
                </tr>
                </#list>
            </tbody>
            </table>
            </#list>
        </div>
        <div class="tab-pane" id="distribution" role="tabpanel" aria-labelledby="distribution-tab">
            <table class="table table-striped">
                <thead>
                <tr><th colspan="2"><h4>Active data per codespace:</h4></th></tr>
                <tr>
                    <th>Codespace</th>
                    <th class="text-right">ET</th>
                    <th class="text-right">VM</th>
                    <th class="text-right">SX</th>
                </tr>
                </thead>
                <tbody>
                <#list body.elements.distribution?sort_by("datasetId") as item>
                <tr>
                    <th>${item.datasetId}</th>
                    <td class="text-right">${item.etCount}</td>
                    <td class="text-right">${item.vmCount}</td>
                    <td class="text-right">${item.sxCount}</td>
                </tr>
                </#list>
                </tbody>
                <tfoot style="border-top: 2px solid #ddd;">
                <tr>
                    <th>Total</th>
                    <th class="text-right">${body.elements.et}</th>
                    <th class="text-right">${body.elements.vm}</th>
                    <th class="text-right">${body.elements.sx}</th>
                </tr>
                </tfoot>
            </table>
        </div>
        <div class="tab-pane" id="admin" role="tabpanel" aria-labelledby="admin-tab">
            <table class="table table-striped">
                <thead>
                    <tr><th colspan="2"><h4>Admin tools:</h4></th></tr>
                </thead>
                <tbody>
                    <tr data-toggle="collapse" data-target="#accordion_admin_terminate" style="cursor: pointer" class="clickable danger">
                        <td colspan="2">Terminate ALL subscriptions</td>
                    </tr>
                    <tr id="accordion_admin_terminate" class="collapse ">
                        <td>
                            CAUTION - terminates ALL subscriptions!!! <br />
                            Will stop <i>all</i> incoming data immediately.<br />
                            Use case: Server is to be taken down controlled, and all subscriptions should be stopped.
                        </td>
                        <td>
                            <span style="cursor: pointer"  class="glyphicon glyphicon-stop text-danger" onclick="administerSubscription('terminateAll', '')"></span>
                        </td>
                    </tr>

                    <tr data-toggle="collapse" data-target="#accordion_admin_start" style="cursor: pointer" class="clickable success">
                        <td colspan="2">Restart ALL active subscriptions</td>
                    </tr>
                    <tr id="accordion_admin_start" class="collapse ">
                        <td>
                            CAUTION - Triggers immediate restart of ALL active subscriptions!!!<br />
                            Use case: Server has just been started, and all subscriptions should be activated ASAP instead of waiting for health-trigger.
                        </td>
                        <td>
                            <span style="cursor: pointer"  class="glyphicon glyphicon-refresh text-success" onclick="administerSubscription('startAll', '')"></span>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <div class="tab-pane" id="map" role="tabpanel" aria-labelledby="map-tab" align="center">

            <button type="button" class="btn btn-success text-success" onclick="showMap()">Show map</button> <br />
            <iframe id="mapFrame" width="90%" height="90%" align="center" frameborder="0"></iframe>
        </div>
    </div>
</div>
</body>
<script>
    function showMap() {
        document.getElementById("mapFrame"). src = "https://jsfiddle.net/lassetyr/cj0xaoyw/show";
    }
    $(function () {
        $(document).ready(function(){
            //Manage hash in URL to open the right tab
            var hash = window.location.hash;
            // If a hash is provided
            if(hash && hash.length>0)
            {
                $('ul.nav-tabs li a').each(function( index ) {
                    if($(this).attr('href')==hash)
                        $(this).parent('li').addClass('active');
                    else
                        $(this).parent('li').removeClass('active');
                });
                // Manage Tab content
                var hash = hash.substring(1); // Remove the #
                $('div.tab-content div').each(function( index ) {
                    if($(this).attr('id')==hash)
                        $(this).addClass('active');
                    else
                        $(this).removeClass('active');
                });
            } else {
                $('.nav-tabs a[href="#inbound"]').tab('show')
            }
        });
    })
</script>
</html>