package citytrade.engine.ruleset;

/**
 * Numbers Sheet 13: formal contract limits and break penalties.
 *
 * @param maxDurationRounds                   latest due round = signing round + this value
 * @param compensationPerUnpaidResource       Money owed to the victim per unpaid resource unit
 * @param compensationPerUnpaidMoney          Money owed to the victim per unpaid Money unit
 * @param unpaidCompensationMoneyPerPrestige  1 Prestige lost per this much unpaid compensation (round up)
 * @param breakPrestigePenalty                Prestige lost for every broken contract
 */
public record ContractRules(
        int maxDurationRounds,
        int compensationPerUnpaidResource,
        int compensationPerUnpaidMoney,
        int unpaidCompensationMoneyPerPrestige,
        int breakPrestigePenalty) {
}
