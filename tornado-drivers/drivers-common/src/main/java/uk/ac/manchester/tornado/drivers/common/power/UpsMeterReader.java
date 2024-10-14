package uk.ac.manchester.tornado.drivers.common.power;

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

    private static String OUTPUT_VOLTAGE_OID = "1.3.6.1.2.1.33.1.4.4.1.2.1";
    private static String OUTPUT_POWER_OID = "1.3.6.1.2.1.33.1.4.4.1.4.1";
    private static String COMMUNITY = "public";
    private static String ADDRESS = "192.168.8.193";
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
