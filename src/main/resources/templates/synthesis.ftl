<html>
<head>
    <title>Anshar synthesis screen</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script>
        function administerSubscription(operation, id, type) {

            var uri = "?operation=" + operation + "&subscriptionId=" + id;
            if (type != undefined) {
                uri += "&type=" + type
            }
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', uri, true);
            xhr.onreadystatechange = function () {
                window.location.reload();
            };
            xhr.send(null);
        }
    </script>
</head>
<body>
<div class="jumbotron text-center">
    <h2>Anshar synthesis</h2>
</div>


<div class="container">

    <h4> STOP MONITORING </h4>


    <table class="table">
        <thead>
        <th align="center">datasetId</th>
        <th align="center">Status</th>
        <th align="center">Nb of stops</th>
        <th align="center">Nb of items</th>
        </thead>
        <tbody>
        <#list body.smStats as smStat>


            <#if smStat.status=='OK'>
                <tr class="success">
            <#else >
                <tr class="danger">
            </#if>


            <td align="center" style="vertical-align: middle; font-size: larger">
                ${smStat.datasetId}
            </td>
            <td align="center" style="vertical-align: middle; font-size: larger">
                ${smStat.status}
            </td>
            <td align="center" style="vertical-align: middle; font-size: larger">
                ${smStat.nbOfStops}
            </td>
            <td align="center" style="vertical-align: middle; font-size: larger">
                ${smStat.nbOfMsg}
            </td>
            </tr>
        </#list>
        </tbody>
    </table>

    <br>
    <br>
    <br>
    <br>
    <br>
    <h4> STOPS WITHOUT MAPPING </h4>
    <table class="table">
        <thead>
        <th align="center">Stops not existing in theorical data</th>

        </thead>
        <tbody>
        <#list body.unmappedStops as unmappedStop>


            <td align="center" style="vertical-align: middle; font-size: larger">
                ${unmappedStop}
            </td>
            </tr>
        </#list>
        </tbody>
    </table>

</div>
</body>
<script>
    $(function () {
        $(document).ready(function () {
            //Manage hash in URL to open the right tab
            var hash = window.location.hash;
            // If a hash is provided
            if (hash && hash.length > 0) {
                $('ul.nav-tabs li a').each(function (index) {
                    if ($(this).attr('href') == hash)
                        $(this).parent('li').addClass('active');
                    else
                        $(this).parent('li').removeClass('active');
                });
                // Manage Tab content
                var hash = hash.substring(1); // Remove the #
                $('div.tab-content div').each(function (index) {
                    if ($(this).attr('id') == hash)
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
