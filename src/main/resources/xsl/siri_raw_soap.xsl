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
                xmlns:siri="http://www.siri.org.uk/siri"
                xmlns:wsa="http://www.w3.org/2005/08/addressing"
                exclude-result-prefixes="xs" version="2.0">

    <xsl:output indent="yes"/>

    <xsl:param name="operatorNamespace"/>
    <xsl:param name="siriSoapNamespace" select="'http://wsdl.siri.org.uk'"/>
    <xsl:param name="siriNamespace" select="'http://www.siri.org.uk/siri'"/>
    <xsl:param name="endpointUrl"/>
    <!-- Configurable namespace for soapenv - use default if not supplied -->
    <xsl:param name="soapEnvelopeNamespace" select="'http://schemas.xmlsoap.org/soap/envelope/'"/>

    <xsl:template match="/siri:Siri">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="siri:ServiceRequest">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="siri:ServiceDelivery">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="siri:SubscriptionResponse">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="siri:StopPointsRequest">
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wsdl="http://wsdl.siri.org.uk"
                          xmlns:siri="http://www.siri.org.uk/siri">
            <soapenv:Header/>
            <soapenv:Body>
                <wsdl:StopPointsDiscovery>
                    <Request version="2.0">
                        <xsl:copy-of select="./siri:RequestTimestamp" copy-namespaces="no">
                        </xsl:copy-of>
                        <xsl:copy-of select="./siri:RequestorRef" copy-namespaces="no">
                        </xsl:copy-of>
                        <xsl:copy-of select="./siri:MessageIdentifier" copy-namespaces="no">
                        </xsl:copy-of>
                    </Request>

                </wsdl:StopPointsDiscovery>
            </soapenv:Body>
        </soapenv:Envelope>
    </xsl:template>

    <xsl:template match="siri:LinesRequest">
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wsdl="http://wsdl.siri.org.uk"
                          xmlns:siri="http://www.siri.org.uk/siri">
            <soapenv:Header/>
            <soapenv:Body>
                <wsdl:LinesDiscovery>
                    <Request version="2.0">
                        <xsl:copy-of select="./siri:RequestTimestamp" copy-namespaces="no">
                        </xsl:copy-of>
                        <xsl:copy-of select="./siri:RequestorRef" copy-namespaces="no">
                        </xsl:copy-of>
                        <xsl:copy-of select="./siri:MessageIdentifier" copy-namespaces="no">
                        </xsl:copy-of>
                    </Request>

                </wsdl:LinesDiscovery>
            </soapenv:Body>
        </soapenv:Envelope>
    </xsl:template>

    <xsl:template match="siri:StopPointsDelivery">
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">
                <xsl:element name="StopPointsDiscoveryResponse" namespace="{$siriSoapNamespace}">

                    <xsl:element name="Answer" namespace="">
                        <xsl:copy-of select="./siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:Status" copy-namespaces="no"/>

                        <xsl:copy-of select="./siri:AnnotatedStopPointRef" copy-namespaces="no">

                        </xsl:copy-of>
                    </xsl:element>

                    <xsl:element name="AnswerExtension" namespace="">
                    </xsl:element>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="siri:LinesDelivery">
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">
                <xsl:element name="LinesDiscoveryResponse" namespace="{$siriSoapNamespace}">

                    <xsl:element name="Answer" namespace="">
                        <xsl:copy-of select="./siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:Status" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:AnnotatedLineRef" copy-namespaces="no"/>
                    </xsl:element>

                    <xsl:element name="AnswerExtension" namespace="">
                    </xsl:element>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="siri:CheckStatusResponse">
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">

                <xsl:element name="CheckStatusResponse" namespace="{$siriSoapNamespace}">
                    <xsl:element name="CheckStatusAnswerInfo" namespace="">
                        <xsl:copy-of select="./siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:ResponseMessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:RequestMessageRef" copy-namespaces="no"/>
                    </xsl:element>
                    <xsl:element name="Answer" namespace="">


                        <xsl:copy-of select="./siri:Status" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:ServiceStartedTime" copy-namespaces="no"/>

                    </xsl:element>

                    <xsl:element name="AnswerExtension" namespace="">
                    </xsl:element>

                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="siri:TerminateSubscriptionResponse">
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">

                <xsl:element name="DeleteSubscriptionResponse" namespace="{$siriSoapNamespace}">
                    <xsl:element name="DeleteSubscriptionAnswerInfo" namespace="">
                        <xsl:copy-of select="./siri:ResponseTimestamp" copy-namespaces="no"/>

                    </xsl:element>

                    <xsl:element name="Answer" namespace="">
                        <xsl:copy-of select="./siri:ResponseTimestamp" copy-namespaces="no"/>

                        <xsl:for-each select="./siri:TerminationResponseStatus">
                            <xsl:element name="siri:{local-name()}">
                                <xsl:copy-of select="./siri:SubscriptionRef" copy-namespaces="no"/>
                                <xsl:copy-of select="./siri:Status" copy-namespaces="no"/>
                            </xsl:element>
                        </xsl:for-each>


                    </xsl:element>

                    <xsl:element name="AnswerExtension" namespace="">
                    </xsl:element>


                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="*"/>

    <xsl:template
            match="siri:VehicleMonitoringRequest | siri:SituationExchangeRequest | siri:EstimatedTimetableRequest | siri:SubscriptionRequest | siri:TerminateSubscriptionRequest | siri:CheckStatusRequest | siri:DataSupplyRequest | siri:StopMonitoringRequest | siri:FacilityMonitoringRequest"> <!-- TODO add all conceptual types of requests -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:choose>
                <xsl:when test="local-name()='SubscriptionRequest'">
                    <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}">
                        <xsl:element name="wsa:Action">Subscribe</xsl:element>
                        <xsl:element name="wsa:To">
                            <xsl:value-of select="$endpointUrl"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:when>
                <xsl:when test="local-name()='TerminateSubscriptionRequest'">
                    <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}">
                        <xsl:element name="wsa:Action">DeleteSubscription</xsl:element>
                        <xsl:element name="wsa:To">
                            <xsl:value-of select="$endpointUrl"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:when>
                <xsl:when test="local-name()='DataSupplyRequest'">
                    <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}">
                        <xsl:element name="wsa:Action">DataSupply</xsl:element>
                        <xsl:element name="wsa:To">
                            <xsl:value-of select="$endpointUrl"/>
                        </xsl:element>
                    </xsl:element>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">

                <xsl:choose>
                    <xsl:when test="local-name()='SubscriptionRequest'">
                        <xsl:element name="Subscribe" namespace="{$operatorNamespace}">
                            <xsl:element name="SubscriptionRequestInfo">
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:Address" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:MessageIdentifier" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:ConsumerAddress" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:SubscriptionContext" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:choose>
                                    <xsl:when
                                            test="/siri:Siri/siri:SubscriptionRequest/siri:SituationExchangeSubscriptionRequest">
                                        <xsl:element name="siri:SituationExchangeSubscriptionRequest">
                                            <xsl:copy-of
                                                    select="siri:SituationExchangeSubscriptionRequest/siri:SubscriptionIdentifier"
                                                    copy-namespaces="no"/>
                                            <xsl:copy-of
                                                    select="siri:SituationExchangeSubscriptionRequest/siri:InitialTerminationTime"
                                                    copy-namespaces="no"/>

                                            <xsl:element name="siri:SituationExchangeRequest">
                                                <xsl:attribute name="version">
                                                    <!-- <xsl:value-of select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/@version"/> -->
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of
                                                        select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/*"
                                                        copy-namespaces="no"/>
                                            </xsl:element>
                                            <xsl:copy-of
                                                    select="siri:SituationExchangeSubscriptionRequest/siri:IncrementalUpdates"
                                                    copy-namespaces="no"/>
                                        </xsl:element>
                                    </xsl:when>
                                    <xsl:when
                                            test="/siri:Siri/siri:SubscriptionRequest/siri:VehicleMonitoringSubscriptionRequest">

                                        <xsl:for-each select="./siri:VehicleMonitoringSubscriptionRequest">


                                            <xsl:element name="siri:{local-name()}">


                                                <xsl:copy-of select="./siri:SubscriberRef" copy-namespaces="no"/>
                                                <xsl:copy-of select="./siri:SubscriptionIdentifier"
                                                             copy-namespaces="no"/>
                                                <xsl:copy-of select="./siri:InitialTerminationTime"
                                                             copy-namespaces="no"/>

                                                <xsl:copy-of select="./siri:VehicleMonitoringRequest"
                                                             copy-namespaces="no"/>

                                                <xsl:copy-of select="./siri:ChangeBeforeUpdates"
                                                             copy-namespaces="no"/>

                                            </xsl:element>
                                        </xsl:for-each>

                                    </xsl:when>
                                    <xsl:when
                                            test="/siri:Siri/siri:SubscriptionRequest/siri:EstimatedTimetableSubscriptionRequest">
                                        <xsl:element name="siri:EstimatedTimetableSubscriptionRequest">
                                            <xsl:copy-of
                                                    select="siri:EstimatedTimetableSubscriptionRequest/siri:SubscriptionIdentifier"
                                                    copy-namespaces="no"/>
                                            <xsl:copy-of
                                                    select="siri:EstimatedTimetableSubscriptionRequest/siri:SubscriberRef"
                                                    copy-namespaces="no"/>

                                            <xsl:copy-of
                                                    select="siri:EstimatedTimetableSubscriptionRequest/siri:InitialTerminationTime"
                                                    copy-namespaces="no"/>

                                            <xsl:element name="siri:EstimatedTimetableRequest">
                                                <xsl:attribute name="version">
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of
                                                        select="siri:EstimatedTimetableSubscriptionRequest/siri:EstimatedTimetableRequest/*"
                                                        copy-namespaces="no"/>
                                            </xsl:element>
                                            <xsl:copy-of
                                                    select="siri:EstimatedTimetableSubscriptionRequest/siri:IncrementalUpdates"
                                                    copy-namespaces="no"/>
                                            <xsl:copy-of
                                                    select="siri:EstimatedTimetableSubscriptionRequest/siri:ChangeBeforeUpdates"
                                                    copy-namespaces="no"/>
                                        </xsl:element>
                                    </xsl:when>
                                    <xsl:when
                                            test="/siri:Siri/siri:SubscriptionRequest/siri:StopMonitoringSubscriptionRequest">


                                        <xsl:for-each select="./siri:StopMonitoringSubscriptionRequest">


                                            <xsl:element name="siri:{local-name()}">


                                                <xsl:copy-of select="./siri:SubscriberRef" copy-namespaces="no"/>
                                                <xsl:copy-of select="./siri:SubscriptionIdentifier"
                                                             copy-namespaces="no"/>
                                                <xsl:copy-of select="./siri:InitialTerminationTime"
                                                             copy-namespaces="no"/>

                                                <xsl:copy-of select="./siri:StopMonitoringRequest"
                                                             copy-namespaces="no"/>

                                                <xsl:copy-of select="./siri:ChangeBeforeUpdates"
                                                             copy-namespaces="no"/>

                                            </xsl:element>
                                        </xsl:for-each>

                                    </xsl:when>

                                    <xsl:when
                                            test="/siri:Siri/siri:SubscriptionRequest/siri:GeneralMessageSubscriptionRequest">
                                        <xsl:element name="siri:GeneralMessageSubscriptionRequest">
                                            <xsl:copy-of
                                                    select="siri:GeneralMessageSubscriptionRequest/siri:SubscriptionIdentifier"
                                                    copy-namespaces="no"/>
                                            <xsl:copy-of
                                                    select="siri:GeneralMessageSubscriptionRequest/siri:InitialTerminationTime"
                                                    copy-namespaces="no"/>

                                            <xsl:element name="siri:GeneralMessageRequest">
                                                <xsl:attribute name="version">
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of
                                                        select="siri:GeneralMessageSubscriptionRequest/siri:GeneralMessageRequest/*"
                                                        copy-namespaces="no"/>
                                            </xsl:element>

                                        </xsl:element>
                                    </xsl:when>
                                    <xsl:when
                                            test="/siri:Siri/siri:SubscriptionRequest/siri:FacilityMonitoringSubscriptionRequest">
                                        <xsl:element name="siri:FacilityMonitoringSubscriptionRequest">
                                            <xsl:copy-of
                                                    select="siri:FacilityMonitoringSubscriptionRequest/siri:SubscriptionIdentifier"
                                                    copy-namespaces="no"/>
                                            <xsl:copy-of
                                                    select="siri:FacilityMonitoringSubscriptionRequest/siri:InitialTerminationTime"
                                                    copy-namespaces="no"/>

                                            <xsl:element name="siri:FacilityMonitoringRequest">
                                                <xsl:attribute name="version">
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of
                                                        select="siri:FacilityMonitoringSubscriptionRequest/siri:FacilityMonitoringRequest/*"
                                                        copy-namespaces="no"/>
                                            </xsl:element>

                                        </xsl:element>
                                    </xsl:when>


                                </xsl:choose>
                            </xsl:element>

                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:when>
                    <xsl:when test="local-name()='TerminateSubscriptionRequest'">
                        <xsl:element name="DeleteSubscription" namespace="{$operatorNamespace}">
                            <xsl:element name="DeleteSubscriptionInfo">
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:Address" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:MessageIdentifier" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:ConsumerAddress" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:copy-of select="siri:SubscriberRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:All" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:SubscriptionRef" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:when>
                    <xsl:when test="local-name()='DataSupplyRequest'">
                        <xsl:element name="DataSupply" namespace="{$operatorNamespace}">
                            <xsl:element name="DataSupplyRequestInfo">
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:ConsumerRef" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:copy-of select="siri:AllData" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:when>
                    <xsl:when test="local-name()='CheckStatusRequest'">
                        <xsl:element name="siri:CheckStatus" namespace="{$operatorNamespace}">
                            <xsl:element name="Request">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:MessageIdentifier" copy-namespaces="no"/>
                            </xsl:element>
                        </xsl:element>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:element name="{concat('wsdl:Get',substring-before(local-name(),'Request'))}"
                                     namespace="http://wsdl.siri.org.uk">
                            <xsl:element name="ServiceRequestInfo">
                                <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                                <xsl:element name="siri:RequestTimestamp">
                                    <xsl:value-of select="../siri:RequestTimestamp"/>
                                </xsl:element>
                                <xsl:element name="siri:RequestorRef">
                                    <xsl:value-of select="../siri:RequestorRef"/>
                                </xsl:element>
                                <xsl:element name="siri:MessageIdentifier">
                                    <xsl:value-of select="../siri:MessageIdentifier"/>
                                </xsl:element>
                                <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                            </xsl:element>


                            <xsl:element name="Request">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:element name="siri:RequestTimestamp">
                                    <xsl:value-of select="siri:RequestTimestamp"/>
                                </xsl:element>
                                <xsl:element name="siri:MessageIdentifier">
                                    <xsl:value-of select="siri:MessageIdentifier"/>
                                </xsl:element>
                                <xsl:if test="siri:LineRef != ''">
                                    <xsl:element name="siri:LineRef">
                                        <xsl:value-of select="siri:LineRef"/>
                                    </xsl:element>
                                </xsl:if>
                                <xsl:if test="local-name()='StopMonitoringRequest'">
                                    <xsl:element name="siri:MonitoringRef">
                                        <xsl:value-of select="siri:MonitoringRef"></xsl:value-of>
                                    </xsl:element>
                                </xsl:if>
                            </xsl:element>
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:otherwise>
                </xsl:choose>


            </xsl:element>
        </xsl:element>


    </xsl:template>

    <!-- Siri to Soap response -->
    <xsl:template
            match="siri:StopMonitoringDelivery"> <!-- TODO add all conceptual types of responses -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">

            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>

            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">


                <xsl:element name="{concat('Get',substring-before(local-name(),'Delivery'), 'Response')}"
                             namespace="{$siriSoapNamespace}">
                    <xsl:element name="ServiceDeliveryInfo">
                        <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:RequestMessageRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseMessageIdentifier" copy-namespaces="no"/>
                    </xsl:element>
                    <xsl:element name="Answer" namespace="">
                        <xsl:if test="local-name()='StopMonitoringDelivery'">
                            <xsl:element name="siri:StopMonitoringDelivery">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:element name="siri:ResponseTimestamp">
                                    <xsl:value-of select="../siri:ResponseTimestamp"/>
                                </xsl:element>
                                <xsl:element name="siri:RequestMessageRef">
                                    <xsl:value-of select="../siri:RequestMessageRef"/>
                                </xsl:element>
                                <xsl:copy-of select="./siri:MonitoredStopVisit" copy-namespaces="no">
                                </xsl:copy-of>
                            </xsl:element>
                        </xsl:if>
                    </xsl:element>
                    <xsl:element name="AnswerExtension" namespace=""/>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template
            match="siri:EstimatedTimetableDelivery"> <!-- TODO add all conceptual types of responses -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">

                <xsl:element name="{concat('Get',substring-before(local-name(),'Delivery'), 'Response')}"
                             namespace="{$siriSoapNamespace}">

                    <xsl:element name="ServiceDeliveryInfo">
                        <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:RequestMessageRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseMessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ErrorCondition" copy-namespaces="no"/>
                    </xsl:element>

                    <xsl:element name="Answer" namespace="">

                        <xsl:if test="local-name()='EstimatedTimetableDelivery'">
                            <xsl:element name="siri:EstimatedTimetableDelivery">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:element name="siri:ResponseTimestamp">
                                    <xsl:value-of select="../siri:ResponseTimestamp"/>
                                </xsl:element>
                                <xsl:element name="siri:RequestMessageRef">
                                    <xsl:value-of select="../siri:RequestMessageRef"/>
                                </xsl:element>
                                <xsl:copy-of select="./siri:EstimatedJourneyVersionFrame" copy-namespaces="no">
                                </xsl:copy-of>
                                <xsl:copy-of select="./siri:ErrorCondition" copy-namespaces="no"/>
                            </xsl:element>
                        </xsl:if>

                    </xsl:element>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template
            match="siri:GeneralMessageDelivery"> <!-- TODO add all conceptual types of responses -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">

                <xsl:element name="{concat('Get',substring-before(local-name(),'Delivery'), 'Response')}"
                             namespace="{$siriSoapNamespace}">

                    <xsl:element name="ServiceDeliveryInfo">
                        <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:RequestMessageRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseMessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ErrorCondition" copy-namespaces="no"/>
                    </xsl:element>

                    <xsl:element name="Answer" namespace="">

                        <xsl:if test="local-name()='GeneralMessageDelivery'">
                            <xsl:element name="siri:GeneralMessageDelivery">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:element name="siri:ResponseTimestamp">
                                    <xsl:value-of select="../siri:ResponseTimestamp"/>
                                </xsl:element>
                                <xsl:element name="siri:RequestMessageRef">
                                    <xsl:value-of select="../siri:RequestMessageRef"/>
                                </xsl:element>
                                <xsl:copy-of select="./siri:GeneralMessage" copy-namespaces="no">
                                </xsl:copy-of>
                                <xsl:copy-of select="./siri:ErrorCondition" copy-namespaces="no"/>
                            </xsl:element>
                        </xsl:if>

                    </xsl:element>
                </xsl:element>
            </xsl:element>
        </xsl:element>


    </xsl:template>

    <xsl:template
            match="siri:FacilityMonitoringDelivery"> <!-- TODO add all conceptual types of responses -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">

                <xsl:element name="{concat('Get',substring-before(local-name(),'Delivery'), 'Response')}"
                             namespace="{$siriSoapNamespace}">

                    <xsl:element name="ServiceDeliveryInfo">
                        <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseMessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ErrorCondition" copy-namespaces="no"/>
                    </xsl:element>

                    <xsl:element name="Answer" namespace="">

                        <xsl:if test="local-name()='FacilityMonitoringDelivery'">
                            <xsl:element name="siri:FacilityMonitoringDelivery">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:element name="siri:ResponseTimestamp">
                                    <xsl:value-of select="../siri:ResponseTimestamp"/>
                                </xsl:element>
                                <xsl:copy-of select="./siri:FacilityCondition" copy-namespaces="no">
                                </xsl:copy-of>
                                <xsl:copy-of select="./siri:ErrorCondition" copy-namespaces="no"/>
                            </xsl:element>
                        </xsl:if>

                    </xsl:element>
                </xsl:element>
            </xsl:element>
        </xsl:element>


    </xsl:template>

    <!-- Siri to Soap response -->
    <xsl:template
            match="siri:VehicleMonitoringDelivery"> <!-- TODO add all conceptual types of responses -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">

            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>

            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">


                <xsl:element name="{concat('Get',substring-before(local-name(),'Delivery'), 'Response')}"
                             namespace="{$siriSoapNamespace}">


                    <xsl:element name="ServiceDeliveryInfo">
                        <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:RequestMessageRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseMessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ErrorCondition" copy-namespaces="no"/>
                    </xsl:element>
                    <xsl:element name="Answer" namespace="">
                        <xsl:if test="local-name()='VehicleMonitoringDelivery'">
                            <xsl:element name="siri:VehicleMonitoringDelivery">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:element name="siri:ResponseTimestamp">
                                    <xsl:value-of select="../siri:ResponseTimestamp"/>
                                </xsl:element>
                                <xsl:element name="siri:RequestMessageRef">
                                    <xsl:value-of select="../siri:RequestMessageRef"/>
                                </xsl:element>
                                <xsl:copy-of select="./siri:VehicleActivity" copy-namespaces="no">
                                </xsl:copy-of>
                                <xsl:copy-of select="./siri:ErrorCondition" copy-namespaces="no"/>
                            </xsl:element>
                        </xsl:if>
                    </xsl:element>
                    <xsl:element name="AnswerExtension" namespace=""/>
                </xsl:element>


            </xsl:element>
        </xsl:element>


    </xsl:template>

    <xsl:template
            match="siri:SituationExchangeDelivery"> <!-- TODO add all conceptual types of responses -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">

            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>

            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">


                <xsl:element name="{concat('Get',substring-before(local-name(),'Delivery'), 'Response')}"
                             namespace="{$siriSoapNamespace}">
                    <xsl:element name="ServiceDeliveryInfo">
                        <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ProducerRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:RequestMessageRef" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                        <xsl:copy-of select="../siri:ResponseMessageIdentifier" copy-namespaces="no"/>
                    </xsl:element>
                    <xsl:element name="Answer" namespace="">
                        <xsl:if test="local-name()='SituationExchangeDelivery'">
                            <xsl:element name="siri:SituationExchangeDelivery">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:element name="siri:ResponseTimestamp">
                                    <xsl:value-of select="../siri:ResponseTimestamp"/>
                                </xsl:element>
                                <xsl:element name="siri:RequestMessageRef">
                                    <xsl:value-of select="../siri:RequestMessageRef"/>
                                </xsl:element>
                                <xsl:copy-of select="./siri:Situations" copy-namespaces="no">
                                </xsl:copy-of>
                            </xsl:element>
                        </xsl:if>
                    </xsl:element>
                    <xsl:element name="AnswerExtension" namespace=""/>
                </xsl:element>


            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="siri:SubscriptionResponse">
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">

            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>

            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">


                <xsl:element name="SubscribeResponse" namespace="{$siriSoapNamespace}">
                    <xsl:element name="SubscriptionAnswerInfo" namespace="">
                        <xsl:copy-of select="./siri:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:ResponderRef" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:RequestMessageRef" copy-namespaces="no"/>
                    </xsl:element>

                    <xsl:element name="Answer" namespace="">

                        <xsl:for-each select="./siri:ResponseStatus">
                            <xsl:element name="siri:{local-name()}">
                                <xsl:copy-of select="./siri:ResponseTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="./siri:RequestMessageRef" copy-namespaces="no"/>
                                <xsl:copy-of select="./siri:SubscriptionRef" copy-namespaces="no"/>
                                <xsl:copy-of select="./siri:Status" copy-namespaces="no"/>
                                <xsl:copy-of select="./siri:ErrorCondition" copy-namespaces="no"/>
                            </xsl:element>
                        </xsl:for-each>
                    </xsl:element>


                    <xsl:element name="AnswerExtension" namespace="">
                    </xsl:element>
                </xsl:element>


            </xsl:element>

        </xsl:element>


    </xsl:template>


</xsl:stylesheet>
