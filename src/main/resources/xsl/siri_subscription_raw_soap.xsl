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

    <xsl:template match="siri:HeartbeatNotification">

        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">
            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>
            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">


                <xsl:element name="NotifyHeartbeat" namespace="{$siriSoapNamespace}">

                    <xsl:element name="HeartbeatNotifyInfo">
                        <xsl:copy-of select="./siri:RequestTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:ProducerRef" copy-namespaces="no"/>
                    </xsl:element>
                    <xsl:element name="Notification">
                        <xsl:copy-of select="./siri:Status" copy-namespaces="no"/>
                        <xsl:copy-of select="./siri:ServiceStartedTime" copy-namespaces="no"/>

                    </xsl:element>
                    <xsl:element name="SiriExtension">
                    </xsl:element>

                </xsl:element>
            </xsl:element>
        </xsl:element>


    </xsl:template>


    <xsl:template match="*"/>


    <!-- Siri to Soap response -->
    <xsl:template
            match="siri:StopMonitoringDelivery"> <!-- TODO add all conceptual types of responses -->
        <xsl:element name="soapenv:Envelope" namespace="{$soapEnvelopeNamespace}">

            <xsl:element name="soapenv:Header" namespace="{$soapEnvelopeNamespace}"/>

            <xsl:element name="soapenv:Body" namespace="{$soapEnvelopeNamespace}">


                <xsl:element name="NotifyStopMonitoring"
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
                    <xsl:element name="Notification">
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
                    <xsl:element name="RequestExtension"/>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>


</xsl:stylesheet>
