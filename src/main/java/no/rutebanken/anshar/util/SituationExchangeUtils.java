package no.rutebanken.anshar.util;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.PtSituationElement;

import java.util.*;
import java.util.stream.Collectors;

public class SituationExchangeUtils {

    private SituationExchangeUtils() {
    }

    public static boolean isAnyOperatorAffected(PtSituationElement ptSituationElement, Set<String> operatorRefsFilter) {
        if (CollectionUtils.isEmpty(operatorRefsFilter)) {
            // no filtering by operator ref
            return true;
        }
        if (ptSituationElement.getAffects() == null || (ptSituationElement.getAffects().getOperators() == null && ptSituationElement.getAffects().getNetworks() == null)) {
            // no affects in SX
            return false;
        }
        Set<String> affectedOperators = new HashSet<>();
        // SX may affect all operators => <Affects><Operators><AllOperators/></Operators></Affects>
        // or operators may be defined at two different levels in SX
        // 1. <Affects><Operators><AffectedOperator><OperatorRef>...</OperatorRef></AffectedOperator></Operators></Affects>
        // 2. <Affects><Networks><AffectedNetwork><AffectedOperator><OperatorRef>...</OperatorRef></AffectedOperator></Networks></Affects>
        if (ptSituationElement.getAffects().getOperators() != null) {
            if (ptSituationElement.getAffects().getOperators().getAllOperators() != null) {
                // affects all operator(s)
                return true;
            }
            ptSituationElement.getAffects().getOperators().getAffectedOperators().stream()
                    .filter(operator -> operator.getOperatorRef() != null && StringUtils.isNotBlank(operator.getOperatorRef().getValue()))
                    .forEach(operator -> affectedOperators.add(operator.getOperatorRef().getValue()));
        }
        if (ptSituationElement.getAffects().getNetworks() != null) {
            ptSituationElement.getAffects().getNetworks().getAffectedNetworks().stream()
                    .flatMap(network -> network.getAffectedOperators().stream())
                    .filter(operator -> operator.getOperatorRef() != null && StringUtils.isNotBlank(operator.getOperatorRef().getValue()))
                    .forEach(operator -> affectedOperators.add(operator.getOperatorRef().getValue()));
        }
        if (CollectionUtils.isEmpty(affectedOperators)) {
            // no operator affected in SX
            return false;
        }
        return affectedOperators.stream().anyMatch(operatorRefsFilter::contains);
    }

    public static boolean isAnyNetworkAffected(PtSituationElement ptSituationElement, Set<String> networkRefsFilter) {
        if (CollectionUtils.isEmpty(networkRefsFilter)) {
            // no filtering by network ref
            return true;
        }
        if (ptSituationElement.getAffects() == null) {
            // no affects in SX
            return false;
        }
        if (ptSituationElement.getAffects().getNetworks() == null) {
            // no network affected in SX
            return false;
        }
        return ptSituationElement.getAffects().getNetworks().getAffectedNetworks().stream()
                .anyMatch(network ->
                        network.getNetworkRef() != null
                                && StringUtils.isNotBlank(network.getNetworkRef().getValue())
                                && networkRefsFilter.contains(network.getNetworkRef().getValue())
                );
    }

    public static boolean isAnyLineAffected(PtSituationElement ptSituationElement, Set<String> lineRefsFilter) {
        if (CollectionUtils.isEmpty(lineRefsFilter)) {
            // no filtering by line ref
            return true;
        }
        if (ptSituationElement.getAffects() == null) {
            return false;
        }
        if (ptSituationElement.getAffects().getNetworks() == null || CollectionUtils.isEmpty(ptSituationElement.getAffects().getNetworks().getAffectedNetworks())) {
            return false;
        }
        for (var affectedNetwork : ptSituationElement.getAffects().getNetworks().getAffectedNetworks()) {
            for (var affectedLine : affectedNetwork.getAffectedLines()) {
                if (affectedLine.getLineRef() != null && StringUtils.isNotBlank(affectedLine.getLineRef().getValue()) && lineRefsFilter.contains(affectedLine.getLineRef().getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<PtSituationElement> filterSituations(Collection<PtSituationElement> situations,
                                                            Set<String> operatorRef, Set<String> networkRef,
                                                            Set<String> lineRefs) {
        if (CollectionUtils.isEmpty(operatorRef) && CollectionUtils.isEmpty(networkRef) && CollectionUtils.isEmpty(lineRefs)) {
            // no filtering needed
            return new ArrayList<>(situations);
        }
        return situations.stream()
                .filter(situation ->
                        isAnyNetworkAffected(situation, networkRef)
                                && isAnyOperatorAffected(situation, operatorRef)
                                && isAnyLineAffected(situation, lineRefs)
                ).collect(Collectors.toCollection(ArrayList::new));

    }

}
