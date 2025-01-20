/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class UpsMeterReader {

    /*
     * The constants are specific identifiers for the EATON 5PX UPS model that is used
     * to query the output voltage and power metrics via the SNMP protocol.
     * The constants can be appended with new values or extended with additional metrics.
     */
    private static String OUTPUT_VOLTAGE_OID = "1.3.6.1.2.1.33.1.4.4.1.2.1";
    private static String OUTPUT_POWER_OID = "1.3.6.1.2.1.33.1.4.4.1.4.1";
    private static String COMMUNITY = "public";
    private static String ADDRESS = TornadoOptions.UPS_IP_ADDRESS;
    private static int SNMP_VERSION = SnmpConstants.version1;

    private static String getSnmpValue(String oid) {
        String result = "";
        try {
            // Create TransportMapping and Listen
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            transport.listen();

            // Create the target
            if (ADDRESS == null) {
                return null;
            }
            Address targetAddress = GenericAddress.parse("udp:" + ADDRESS + "/161");
            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(COMMUNITY));
            target.setAddress(targetAddress);
            target.setRetries(2);
            target.setTimeout(1500);
            target.setVersion(SNMP_VERSION);

            // Create the PDU for SNMP GET
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            // Create the SNMP object and send the request
            Snmp snmp = new Snmp(transport);
            ResponseEvent response = snmp.get(pdu, target);

            // Process the response
            if (response != null && response.getResponse() != null) {
                result = response.getResponse().get(0).getVariable().toString();
            } else {
                System.err.println("Error: No response from SNMP agent.");
            }
            snmp.close();
        } catch (Exception e) {
            System.err.println("Error in SNMP GET: " + e.getMessage());
        }
        return result;
    }

    public static String getOutputPowerMetric() {
        return getSnmpValue(OUTPUT_POWER_OID);
    }

    public static String getOutputVoltageMetric() {
        return getSnmpValue(OUTPUT_VOLTAGE_OID);
    }

}
