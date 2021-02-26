#!/usr/bin/env bash

curl -X POST -H "Content-Type: text/xml" \
    -H "X-Cda-Lr-Api-Key: f6002af3-80bc-48bb-b3b2-4ecad5e77936" \
    --data-binary @request.xml \
    "https://api.agglo-larochelle.fr/production/siri-yelo/capsiria/services/siri" \
    -o response.xml
#    | xmlstarlet format --indent-tab

