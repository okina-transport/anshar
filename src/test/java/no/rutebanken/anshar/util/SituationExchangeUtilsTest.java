package no.rutebanken.anshar.util;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SituationExchangeUtilsTest {

    private PtSituationElement situation(String situationNumber, String lineRefValue, String operatorRefValue, String networkRefValue) {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue(situationNumber);
        element.setSituationNumber(sn);

        if (lineRefValue == null && operatorRefValue == null && networkRefValue == null) {
            return element;
        }

        AffectsScopeStructure affects = new AffectsScopeStructure();

        if (lineRefValue != null || networkRefValue != null) {
            AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
            AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();

            if (networkRefValue != null) {
                NetworkRefStructure networkRef = new NetworkRefStructure();
                networkRef.setValue(networkRefValue);
                affectedNetwork.setNetworkRef(networkRef);
            }

            if (lineRefValue != null) {
                AffectedLineStructure affectedLine = new AffectedLineStructure();
                LineRef lineRef = new LineRef();
                lineRef.setValue(lineRefValue);
                affectedLine.setLineRef(lineRef);
                affectedNetwork.getAffectedLines().add(affectedLine);
            }

            networks.getAffectedNetworks().add(affectedNetwork);
            affects.setNetworks(networks);
        }

        if (operatorRefValue != null) {
            AffectsScopeStructure.Operators operators = new AffectsScopeStructure.Operators();
            AffectedOperatorStructure affectedOperator = new AffectedOperatorStructure();
            OperatorRefStructure operatorRef = new OperatorRefStructure();
            operatorRef.setValue(operatorRefValue);
            affectedOperator.setOperatorRef(operatorRef);
            operators.getAffectedOperators().add(affectedOperator);
            affects.setOperators(operators);
        }

        element.setAffects(affects);
        return element;
    }

    private PtSituationElement situationWithNetworkLevelOperator(String situationNumber, String networkRefValue, String operatorRefValue) {
        PtSituationElement element = situation(situationNumber, null, null, networkRefValue);

        AffectedOperatorStructure affectedOperator = new AffectedOperatorStructure();
        OperatorRefStructure operatorRef = new OperatorRefStructure();
        operatorRef.setValue(operatorRefValue);
        affectedOperator.setOperatorRef(operatorRef);

        element.getAffects().getNetworks().getAffectedNetworks().get(0).getAffectedOperators().add(affectedOperator);
        return element;
    }

    private PtSituationElement situationAffectingAllOperators(String situationNumber) {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue(situationNumber);
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.Operators operators = new AffectsScopeStructure.Operators();
        operators.setAllOperators("");
        affects.setOperators(operators);
        element.setAffects(affects);
        return element;
    }

    // --- isAnyOperatorAffected ---

    @Test
    public void isAnyOperatorAffected_emptyFilter_matchesAnything() {
        PtSituationElement element = situation("SIT:1", null, null, null);
        assertTrue(SituationExchangeUtils.isAnyOperatorAffected(element, Collections.emptySet()));
        assertTrue(SituationExchangeUtils.isAnyOperatorAffected(element, null));
    }

    @Test
    public void isAnyOperatorAffected_noAffects_doesNotMatchConfiguredFilter() {
        PtSituationElement element = situation("SIT:1", null, null, null);
        assertFalse(SituationExchangeUtils.isAnyOperatorAffected(element, Set.of("OP:1")));
    }

    @Test
    public void isOperatorAffected_matchingAnyOperator_returnsTrue() {
        PtSituationElement element = situation("SIT:1", null, "OP:TARGET", null);
        assertTrue(SituationExchangeUtils.isAnyOperatorAffected(element, Set.of("OP:TARGET")));
    }

    @Test
    public void isOperatorAffected_nonMatchingAnyOperator_returnsFalse() {
        PtSituationElement element = situation("SIT:1", null, "OP:OTHER", null);
        assertFalse(SituationExchangeUtils.isAnyOperatorAffected(element, Set.of("OP:TARGET")));
    }

    @Test
    public void isOperatorAffected_matchingOperatorNestedUnderNetwork_returnsTrue() {
        PtSituationElement element = situationWithNetworkLevelOperator("SIT:1", "NET:A", "OP:TARGET");
        assertTrue(SituationExchangeUtils.isAnyOperatorAffected(element, Set.of("OP:TARGET")));
    }

    @Test
    public void isOperatorAffected_nonMatchingOperatorNestedUnderNetwork_returnsFalse() {
        PtSituationElement element = situationWithNetworkLevelOperator("SIT:1", "NET:A", "OP:OTHER");
        assertFalse(SituationExchangeUtils.isAnyOperatorAffected(element, Set.of("OP:TARGET")));
    }

    @Test
    public void isOperatorAffected_networkPresentWithoutAnyOperator_returnsFalse() {
        PtSituationElement element = situation("SIT:1", "LINE:A", null, "NET:A");
        assertFalse(SituationExchangeUtils.isAnyOperatorAffected(element, Set.of("OP:TARGET")));
    }

    @Test
    public void isOperatorAffected_allOperatorsFlagSet_matchesRegardlessOfFilter() {
        PtSituationElement element = situationAffectingAllOperators("SIT:1");
        assertTrue(SituationExchangeUtils.isAnyOperatorAffected(element, Set.of("OP:SOME_OTHER")));
    }

    // --- isAnyNetworkAffected ---

    @Test
    public void isAnyNetworkAffected_emptyFilter_matchesAnything() {
        PtSituationElement element = situation("SIT:1", null, null, null);
        assertTrue(SituationExchangeUtils.isAnyNetworkAffected(element, Collections.emptySet()));
        assertTrue(SituationExchangeUtils.isAnyNetworkAffected(element, null));
    }

    @Test
    public void isAnyNetworkAffected_noAffects_doesNotMatchConfiguredFilter() {
        PtSituationElement element = situation("SIT:1", null, null, null);
        assertFalse(SituationExchangeUtils.isAnyNetworkAffected(element, Set.of("NET:1")));
    }

    @Test
    public void isNetworkAffected_matchingAnyNetwork_returnsTrue() {
        PtSituationElement element = situation("SIT:1", null, null, "NET:TARGET");
        assertTrue(SituationExchangeUtils.isAnyNetworkAffected(element, Set.of("NET:TARGET")));
    }

    @Test
    public void isNetworkAffected_nonMatchingAnyNetwork_returnsFalse() {
        PtSituationElement element = situation("SIT:1", null, null, "NET:OTHER");
        assertFalse(SituationExchangeUtils.isAnyNetworkAffected(element, Set.of("NET:TARGET")));
    }

    // --- isAnyLineAffected ---

    @Test
    public void isAnyLineAffected_emptyFilter_matchesAnything() {
        PtSituationElement element = situation("SIT:1", null, null, null);
        assertTrue(SituationExchangeUtils.isAnyLineAffected(element, Collections.emptySet()));
        assertTrue(SituationExchangeUtils.isAnyLineAffected(element, null));
    }

    @Test
    public void isAnyLineAffected_noAffects_doesNotMatchConfiguredFilter() {
        PtSituationElement element = situation("SIT:1", null, null, null);
        assertFalse(SituationExchangeUtils.isAnyLineAffected(element, Set.of("LINE:1")));
    }

    @Test
    public void isAnyLineAffected_matchingLine_returnsTrue() {
        PtSituationElement element = situation("SIT:1", "LINE:TARGET", null, "NET:1");
        assertTrue(SituationExchangeUtils.isAnyLineAffected(element, Set.of("LINE:TARGET")));
    }

    @Test
    public void isAnyLineAffected_nonMatchingLine_returnsFalse() {
        PtSituationElement element = situation("SIT:1", "LINE:OTHER", null, "NET:1");
        assertFalse(SituationExchangeUtils.isAnyLineAffected(element, Set.of("LINE:TARGET")));
    }

    // --- filterSituations ---

    @Test
    public void filterSituations_noFiltersConfigured_returnsAllSituationsUnchanged() {
        List<PtSituationElement> situations = new ArrayList<>(Arrays.asList(
                situation("SIT:1", "LINE:A", "OP:A", "NET:A"),
                situation("SIT:2", null, null, null)
        ));

        List<PtSituationElement> filtered = SituationExchangeUtils.filterSituations(
                situations, Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

        assertEquals(2, filtered.size());
    }

    @Test
    public void filterSituations_byOperatorOnly_keepsOnlyMatches() {
        List<PtSituationElement> situations = Arrays.asList(
                situation("SIT:1", "LINE:A", "OP:TARGET", null),
                situation("SIT:2", "LINE:B", "OP:OTHER", null)
        );

        List<PtSituationElement> filtered = SituationExchangeUtils.filterSituations(
                situations, Set.of("OP:TARGET"), Collections.emptySet(), Collections.emptySet());

        assertEquals(1, filtered.size());
        assertEquals("SIT:1", filtered.get(0).getSituationNumber().getValue());
    }

    @Test
    public void filterSituations_byNetworkOnly_keepsOnlyMatches() {
        List<PtSituationElement> situations = Arrays.asList(
                situation("SIT:1", "LINE:A", null, "NET:TARGET"),
                situation("SIT:2", "LINE:B", null, "NET:OTHER")
        );

        List<PtSituationElement> filtered = SituationExchangeUtils.filterSituations(
                situations, Collections.emptySet(), Set.of("NET:TARGET"), Collections.emptySet());

        assertEquals(1, filtered.size());
        assertEquals("SIT:1", filtered.get(0).getSituationNumber().getValue());
    }

    @Test
    public void filterSituations_byLineOnly_keepsOnlyMatches() {
        List<PtSituationElement> situations = Arrays.asList(
                situation("SIT:1", "LINE:TARGET", null, "NET:A"),
                situation("SIT:2", "LINE:OTHER", null, "NET:A")
        );

        List<PtSituationElement> filtered = SituationExchangeUtils.filterSituations(
                situations, Collections.emptySet(), Collections.emptySet(), Set.of("LINE:TARGET"));

        assertEquals(1, filtered.size());
        assertEquals("SIT:1", filtered.get(0).getSituationNumber().getValue());
    }

    @Test
    public void filterSituations_allFiltersConfigured_requiresMatchOnEveryType() {
        List<PtSituationElement> situations = Arrays.asList(
                // matches all three
                situation("SIT:1", "LINE:TARGET", "OP:TARGET", "NET:TARGET"),
                // matches operator+network only, wrong line
                situation("SIT:2", "LINE:OTHER", "OP:TARGET", "NET:TARGET"),
                // matches line+network only, wrong operator
                situation("SIT:3", "LINE:TARGET", "OP:OTHER", "NET:TARGET")
        );

        List<PtSituationElement> filtered = SituationExchangeUtils.filterSituations(
                situations, Set.of("OP:TARGET"), Set.of("NET:TARGET"), Set.of("LINE:TARGET"));

        assertEquals(1, filtered.size());
        assertEquals("SIT:1", filtered.get(0).getSituationNumber().getValue());
    }

    @Test
    public void filterSituations_emptyInput_returnsEmptyList() {
        List<PtSituationElement> filtered = SituationExchangeUtils.filterSituations(
                Collections.emptyList(), Set.of("OP:TARGET"), Collections.emptySet(), Collections.emptySet());

        assertTrue(filtered.isEmpty());
    }
}
