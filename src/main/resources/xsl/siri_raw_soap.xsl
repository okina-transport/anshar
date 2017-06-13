<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:siri="http://www.siri.org.uk/siri"
    xmlns:wsa="http://www.w3.org/2005/08/addressing"
    exclude-result-prefixes="xs" version="2.0">

    <xsl:output indent="yes"/>

    <xsl:param name="operatorNamespace"/>
    <xsl:param name="endpointUrl"/>
    <!-- Configurable namespace for soapenv - use default if not supplied -->
    <xsl:param name="soapEnvelopeNamespace" select="'http://schemas.xmlsoap.org/soap/envelope/'"/>

    <xsl:template match="/siri:Siri">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="siri:ServiceRequest">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="*"/>

    <xsl:template match="siri:VehicleMonitoringRequest | siri:SituationExchangeRequest | siri:EstimatedTimetableRequest | siri:SubscriptionRequest | siri:TerminateSubscriptionRequest"> <!-- TODO add all conseptual types of requests -->
            <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}" >

                <xsl:choose>
                    <xsl:when test="local-name()='SubscriptionRequest'">
                        <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}" >
                            <xsl:element name="wsa:Action">Subscribe</xsl:element>
                            <xsl:element name="wsa:To" ><xsl:value-of select="$endpointUrl" /></xsl:element>
                        </xsl:element>
                    </xsl:when>
                    <xsl:when test="local-name()='TerminateSubscriptionRequest'">
                        <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}" >
                            <xsl:element name="wsa:Action">DeleteSubscription</xsl:element>
                        </xsl:element>
                    </xsl:when>
                <xsl:otherwise>
                    <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}" />
                </xsl:otherwise>
            </xsl:choose>
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}" >

                <xsl:choose>
                    <xsl:when test="local-name()='SubscriptionRequest'">
                        <xsl:element name="Subscribe" namespace="{$operatorNamespace}">
                            <xsl:element name="SubscriptionRequestInfo">
                                <xsl:copy-of select="siri:SubscriptionContext" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:MessageIdentifier" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:ConsumerAddress" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:Address" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:choose>
                                    <xsl:when test="/siri:Siri/siri:SubscriptionRequest/siri:SituationExchangeSubscriptionRequest">
                                         <xsl:element name="siri:SituationExchangeSubscriptionRequest">
                                             <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:SubscriptionIdentifier" copy-namespaces="no"/>
                                             <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:InitialTerminationTime" copy-namespaces="no"/>

                                             <xsl:element name="siri:SituationExchangeRequest">
                                                 <xsl:attribute name="version">
                                                     <!-- <xsl:value-of select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/@version"/> -->
                                                     <xsl:value-of select="1.4"/>
                                                 </xsl:attribute>
                                                 <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/*" copy-namespaces="no"/>
                                             </xsl:element>
                                             <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:IncrementalUpdates" copy-namespaces="no"/>
                                         </xsl:element>
                                    </xsl:when>
                                    <xsl:when test="/siri:Siri/siri:SubscriptionRequest/siri:VehicleMonitoringSubscriptionRequest">
                                        <xsl:element name="siri:VehicleMonitoringSubscriptionRequest">
                                            <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:SubscriptionIdentifier" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:InitialTerminationTime" copy-namespaces="no"/>

                                            <xsl:element name="siri:VehicleMonitoringRequest">
                                                <xsl:attribute name="version">
                                                    <!-- <xsl:value-of select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/@version"/> -->
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:VehicleMonitoringRequest/*" copy-namespaces="no"/>
                                            </xsl:element>
                                            <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:IncrementalUpdates" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:ChangeBeforeUpdates" copy-namespaces="no"/>
                                        </xsl:element>
                                    </xsl:when>
                                    <xsl:when test="/siri:Siri/siri:SubscriptionRequest/siri:EstimatedTimetableSubscriptionRequest">
                                        <xsl:element name="siri:EstimatedTimetableSubscriptionRequest">
                                            <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:SubscriptionIdentifier" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:InitialTerminationTime" copy-namespaces="no"/>

                                            <xsl:element name="siri:EstimatedTimetableRequest">
                                                <xsl:attribute name="version">
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:EstimatedTimetableRequest/*" copy-namespaces="no"/>
                                            </xsl:element>
                                            <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:IncrementalUpdates" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:ChangeBeforeUpdates" copy-namespaces="no"/>
                                        </xsl:element>
                                    </xsl:when>
                                </xsl:choose>
                            </xsl:element>

                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:when>
                    <xsl:when test="local-name()='TerminateSubscriptionRequest'">
                        <xsl:element name="siri:DeleteSubscription" namespace="{$operatorNamespace}">
                            <xsl:element name="DeleteSubscriptionInfo">
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:Address" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:ConsumerAddress" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:MessageIdentifier" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:copy-of select="siri:SubscriberRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:All" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:SubscriptionRef" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:element name="{concat('Get',substring-before(local-name(),'Request'))}" namespace="{$operatorNamespace}">
                            <xsl:element name="ServiceRequestInfo">
                                <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:ConsumerAddress" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
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

                            </xsl:element>
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:otherwise>
                </xsl:choose>



            </xsl:element>
        </xsl:element>


    </xsl:template>



</xsl:stylesheet>
