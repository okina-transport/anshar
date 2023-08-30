<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
  ~ the European Commission - subsequent versions of the EUPL (the "Licence");
  ~ You may not use this work except in compliance with the Licence.
  ~ You may obtain a copy of the Licence at:
  ~
  ~   https://joinup.ec.europa.eu/software/page/eupl
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the Licence is distributed on an "AS IS" basis,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the Licence for the specific language governing permissions and
  ~ limitations under the Licence.
  -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:soapenv2="http://www.w3.org/2003/05/soap-envelope"
                xmlns:siri="http://www.siri.org.uk/siri"
                xmlns:siril="http://www.siri.org.uk/siri"
                exclude-result-prefixes="xs" version="2.0">

    <xsl:output indent="yes"/>

    <xsl:param name="operatorNamespace"/>

    <!-- If not SOAP-envelope - copy all as-is-->
    <xsl:template match="/siri:Siri">
        <xsl:element name="siri:Siri">
            <xsl:choose>
                <xsl:when test="child::node()/local-name()='HeartbeatNotification'">
                    <xsl:element name="siri:HeartbeatNotification">
                        <xsl:copy-of select="child::node()/*"/>
                    </xsl:element>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of select="*"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>

    <xsl:template match="/soapenv:Envelope">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="soapenv:Body">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="soapenv:Header"/>

    <xsl:template match="/soapenv2:Envelope">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="soapenv2:Body">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="soapenv2:Header"/>

    <xsl:template match="*"/>

    <xsl:template
            match="*:NotifyVehicleMonitoring | *:NotifySituationExchange | *:NotifyEstimatedTimetable | *:NotifyStopMonitoring | *:NotifyHeartbeat | *:GetVehicleMonitoringResponse | *:GetSituationExchangeResponse | *:GetStopMonitoringResponse | *:GetEstimatedTimetable | *:GetEstimatedTimetableResponse | *:SubscribeResponse | *:DeleteSubscriptionResponse | *:HeartbeatNotification | *:SituationExchangeAnswer | *:VehicleMonitoringAnswer | *:CheckStatusResponse | *:DataSupplyResponse | *:GetStopMonitoring | *:GetVehicleMonitoring | *:StopPointsDiscovery | *:LinesDiscovery | *:GetSituationExchange | *:GetGeneralMessage | *:GetGeneralMessageResponse  | *:GetFacilityMonitoring | *:GetFacilityMonitoringResponse  | *:Subscribe | *:CheckStatus |*:StopPointsDiscoveryResponse | *:LinesDiscoveryResponse"> <!-- TODO add all conseptual types of requests -->

        <xsl:choose>
            <xsl:when test="local-name()='SubscribeResponse'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:SubscriptionResponse">

                        <xsl:for-each select="SubscriptionAnswerInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="SubscriptionAnswerInfo/siril:ResponderRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="Answer/siri:ResponseStatus">
                            <xsl:element name="siril:ResponseStatus">
                                <xsl:for-each select="*">
                                    <xsl:element name="siril:{local-name()}">
                                        <xsl:apply-templates select="* | node()"/>
                                        <xsl:for-each select="*">
                                            <xsl:element name="siril:{local-name()}">
                                                <xsl:apply-templates select="* | node()"/>
                                            </xsl:element>
                                        </xsl:for-each>
                                    </xsl:element>
                                </xsl:for-each>
                            </xsl:element>
                        </xsl:for-each>

                    </xsl:element>
                </xsl:element>
            </xsl:when>
            <xsl:when test="local-name()='SituationExchangeAnswer'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceDelivery">

                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ProducerRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:element name="siril:SituationExchangeDelivery">
                            <xsl:copy-of select="Answer/SituationExchangeDelivery/*" copy-namespaces="no"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:when>
            <xsl:when test="local-name()='VehicleMonitoringAnswer'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceDelivery">

                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ProducerRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:element name="siril:VehicleMonitoringDelivery">
                            <xsl:copy-of select="Answer/VehicleMonitoringDelivery/*" copy-namespaces="no"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:when>
            <xsl:when test="local-name()='HeartbeatNotification'">
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="Answer/child::node()/@version"/>
                    </xsl:attribute>
                    <xsl:element name="siril:HeartbeatNotification">
                        <xsl:copy-of select="*" copy-namespaces="no"/>
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='NotifyHeartbeat'">
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="'2.0'"/>
                    </xsl:attribute>
                    <xsl:element name="siril:HeartbeatNotification">
                        <xsl:copy-of select="HeartbeatNotifyInfo/*"/>
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when
                    test="local-name()='NotifyEstimatedTimetable' or local-name()='NotifyVehicleMonitoring' or local-name()='NotifySituationExchange' or local-name()='NotifyStopMonitoring'">
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="'2.0'"/>
                    </xsl:attribute>
                    <xsl:element name="siril:ServiceDelivery">
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ProducerRef" copy-namespaces="no"/>

                        <xsl:copy-of select="Notification/*"/>
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='DeleteSubscriptionResponse'">

                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:TerminateSubscriptionResponse">

                        <xsl:for-each select="DeleteSubscriptionAnswerInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="DeleteSubscriptionAnswerInfo/siril:ResponderRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="Answer/siri:TerminationResponseStatus">
                            <xsl:element name="siril:TerminationResponseStatus">
                                <xsl:for-each select="*">
                                    <xsl:element name="siril:{local-name()}">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>
                            </xsl:element>
                        </xsl:for-each>

                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='CheckStatusResponse'">
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="Answer/child::node()/@version"/>
                    </xsl:attribute>
                    <xsl:element name="siril:CheckStatusResponse">
                        <xsl:copy-of select="Answer/*" copy-namespaces="no"/>
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='GetSituationExchange'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceRequest">
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:MessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="Request/siril:MonitoringRef">
                            <xsl:element name="siril:StopMonitoringRequest">
                                <xsl:element name="siril:{local-name()}">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:element>
                        </xsl:for-each>


                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ProducerRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:element name="siril:SituationExchangeRequest">
                            <xsl:copy-of select="Answer/SituationExchangeDelivery/*" copy-namespaces="no"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='GetStopMonitoring'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceRequest">
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:MessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="Request/siril:MonitoringRef">
                            <xsl:element name="siril:StopMonitoringRequest">
                                <xsl:element name="siril:{local-name()}">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:element>
                        </xsl:for-each>


                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ProducerRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:element name="siril:SituationExchangeDelivery">
                            <xsl:copy-of select="Answer/SituationExchangeDelivery/*" copy-namespaces="no"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='CheckStatus'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:CheckStatusRequest">

                        <xsl:for-each select="Request/siril:RequestorRef">
                            <xsl:element name="siril:RequestorRef">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>


                        <xsl:for-each select="Request/siril:MessageIdentifier">
                            <xsl:element name="siril:MessageIdentifier">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='GetEstimatedTimetable'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceRequest">
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:MessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:element name="siril:EstimatedTimetableRequest">
                            <xsl:for-each select="Request/siril:RequestTimestamp">
                                <xsl:element name="siril:RequestTimestamp">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>
                            <xsl:for-each select="Request/siril:MessageIdentifier">
                                <xsl:element name="siril:MessageIdentifier">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>
                            <xsl:for-each select="Request/siril:TimetableVersionRef">
                                <xsl:element name="siril:TimetableVersionRef">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>
                            <xsl:for-each select="Request/siril:LineRef">
                                <xsl:element name="siril:LineRef">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>
                        </xsl:element>

                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='GetGeneralMessage'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceRequest">
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:MessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:element name="siril:GeneralMessageRequest">
                            <xsl:for-each select="Request/siril:InfoChannelRef">
                                <xsl:element name="siril:InfoChannelRef">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>


                        </xsl:element>

                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='GetFacilityMonitoring'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceRequest">
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:element name="siril:FacilityMonitoringRequest">

                            <xsl:for-each select="Request/siril:FacilityRef ">
                                <xsl:element name="siril:FacilityRef ">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>

                            <xsl:for-each select="Request/siril:LineRef ">
                                <xsl:element name="siril:LineRef ">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>

                            <xsl:for-each select="Request/siril:StopPointRef ">
                                <xsl:element name="siril:StopPointRef ">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>

                            <xsl:for-each select="Request/siril:VehicleRef ">
                                <xsl:element name="siril:VehicleRef ">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:for-each>

                            <!-- todo StopPlaceRef StopPlaceComponentRef SiteRef-->

                        </xsl:element>

                    </xsl:element>
                </xsl:element>
            </xsl:when>


            <xsl:when test="local-name()='StopPointsDiscovery'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:Request">
                        <xsl:for-each select="Request/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="Request/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                    </xsl:element>

                    <xsl:element name="siril:StopPointsRequest">


                    </xsl:element>
                </xsl:element>
            </xsl:when>


            <xsl:when test="local-name()='StopPointsDiscoveryResponse'">
                <xsl:element name="Siri" namespace="http://www.siri.org.uk/siri">
                    <xsl:element name="StopPointsDelivery" namespace="http://www.siri.org.uk/siri">

                        <xsl:copy-of select="Answer/siril:AnnotatedStopPointRef" copy-namespaces="no">
                        </xsl:copy-of>

                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='LinesDiscoveryResponse'">
                <xsl:element name="Siri" namespace="http://www.siri.org.uk/siri">
                    <xsl:element name="LinesDelivery" namespace="http://www.siri.org.uk/siri">

                        <xsl:copy-of select="Answer/siril:AnnotatedLineRef" copy-namespaces="no">
                        </xsl:copy-of>

                    </xsl:element>
                </xsl:element>
            </xsl:when>


            <xsl:when test="local-name()='Subscribe'">
                <xsl:element name="Siri" namespace="http://www.siri.org.uk/siri">
                    <xsl:element name="SubscriptionRequest" namespace="http://www.siri.org.uk/siri">


                        <xsl:for-each select="SubscriptionRequestInfo/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="siril:SubscriptionRequestInfo/siril:RequestorRef">

                            <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="SubscriptionRequestInfo/siril:ConsumerAddress">
                            <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>


                        <xsl:element name="SubscriptionContext" namespace="http://www.siri.org.uk/siri">
                            <xsl:copy-of select="SubscriptionRequestInfo/siril:SubscriptionContext/*"
                                         copy-namespaces="no"/>
                        </xsl:element>


                        <xsl:for-each select="Request/siril:StopMonitoringSubscriptionRequest">
                            <xsl:element name="StopMonitoringSubscriptionRequest"
                                         namespace="http://www.siri.org.uk/siri">

                                <xsl:for-each select="siril:SubscriptionIdentifier">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:InitialTerminationTime">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:StopMonitoringRequest">
                                    <xsl:element name="StopMonitoringRequest" namespace="http://www.siri.org.uk/siri">
                                        <xsl:copy-of select="*" copy-namespaces="no"/>
                                    </xsl:element>
                                </xsl:for-each>

                            </xsl:element>

                        </xsl:for-each>


                        <xsl:for-each select="Request/siril:VehicleMonitoringSubscriptionRequest">
                            <xsl:element name="VehicleMonitoringSubscriptionRequest"
                                         namespace="http://www.siri.org.uk/siri">

                                <xsl:for-each select="siril:SubscriptionIdentifier">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:InitialTerminationTime">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:VehicleMonitoringRequest">
                                    <xsl:element name="VehicleMonitoringRequest"
                                                 namespace="http://www.siri.org.uk/siri">
                                        <xsl:copy-of select="*" copy-namespaces="no"/>
                                    </xsl:element>
                                </xsl:for-each>

                            </xsl:element>

                        </xsl:for-each>


                        <xsl:for-each select="Request/siril:GeneralMessageSubscriptionRequest">
                            <xsl:element name="GeneralMessageSubscriptionRequest"
                                         namespace="http://www.siri.org.uk/siri">

                                <xsl:for-each select="siril:SubscriptionIdentifier">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:InitialTerminationTime">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:GeneralMessageRequest">
                                    <xsl:element name="GeneralMessageRequest" namespace="http://www.siri.org.uk/siri">
                                        <xsl:copy-of select="*" copy-namespaces="no"/>
                                    </xsl:element>
                                </xsl:for-each>

                            </xsl:element>

                        </xsl:for-each>

                        <xsl:for-each select="Request/siril:FacilityMonitoringSubscriptionRequest">
                            <xsl:element name="FacilityMonitoringSubscriptionRequest"
                                         namespace="http://www.siri.org.uk/siri">

                                <xsl:for-each select="siril:SubscriptionIdentifier">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:InitialTerminationTime">
                                    <xsl:element name="{local-name()}" namespace="http://www.siri.org.uk/siri">
                                        <xsl:apply-templates select="* | node()"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:FacilityMonitoringRequest">
                                    <xsl:element name="FacilityMonitoringRequest"
                                                 namespace="http://www.siri.org.uk/siri">
                                        <xsl:copy-of select="*" copy-namespaces="no"/>
                                    </xsl:element>
                                </xsl:for-each>

                                <xsl:for-each select="siril:IncrementalUpdates">
                                    <xsl:element name="IncrementalUpdates" namespace="http://www.siri.org.uk/siri">
                                        <xsl:copy-of select="*" copy-namespaces="no"/>
                                    </xsl:element>
                                </xsl:for-each>
                            </xsl:element>

                        </xsl:for-each>


                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='LinesDiscovery'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:Request">
                        <xsl:for-each select="Request/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="Request/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                    </xsl:element>

                    <xsl:element name="siril:LinesRequest">


                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='GetVehicleMonitoring'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceRequest">
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestorRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:RequestTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceRequestInfo/siril:MessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>

                        <xsl:for-each select="Request/siril:LineRef">
                            <xsl:element name="siril:VehicleMonitoringRequest">
                                <xsl:element name="siril:{local-name()}">
                                    <xsl:apply-templates select="* | node()"/>
                                </xsl:element>
                            </xsl:element>
                        </xsl:for-each>


                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ProducerRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:element name="siril:SituationExchangeDelivery">
                            <xsl:copy-of select="Answer/SituationExchangeDelivery/*" copy-namespaces="no"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:otherwise>
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="Answer/child::node()/@version"/>
                    </xsl:attribute>
                    <xsl:element name="siril:ServiceDelivery">
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:Address" copy-namespaces="no"/>
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ConsumerAddress" copy-namespaces="no"/>
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:RequestMessageRef" copy-namespaces="no"/>
                        <xsl:copy-of select="Answer/*"/>
                    </xsl:element>
                </xsl:element>
            </xsl:otherwise>
        </xsl:choose>


    </xsl:template>


</xsl:stylesheet>
