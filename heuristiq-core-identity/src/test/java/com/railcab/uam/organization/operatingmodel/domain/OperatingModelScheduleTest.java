package com.heuristiq.uam.organization.operatingmodel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An organization runs DIRECT, FRANCHISE or HYBRID; the model changes only from a chosen date and past days never change. */
class OperatingModelScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Test
    void anOrganizationThatNeverChoseIsDirect() {
        assertThat(OperatingModelSchedule.empty().modelOn(TODAY)).isEqualTo(OperatingModel.DIRECT);
        assertThat(OperatingModelSchedule.of(List.of()).modelOn(TODAY)).isEqualTo(OperatingModel.DIRECT);
    }

    @Test
    void theModelChangesOnTheChosenDayAndNotBefore() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty()
                .withChange(OperatingModel.FRANCHISE, TODAY.plusDays(10), TODAY);

        assertThat(schedule.modelOn(TODAY.plusDays(9))).isEqualTo(OperatingModel.DIRECT);
        assertThat(schedule.modelOn(TODAY.plusDays(10))).isEqualTo(OperatingModel.FRANCHISE);
        assertThat(schedule.modelOn(TODAY.plusYears(1))).isEqualTo(OperatingModel.FRANCHISE);
    }

    @Test
    void aChangeMayStartToday() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty().withChange(OperatingModel.FRANCHISE, TODAY, TODAY);

        assertThat(schedule.modelOn(TODAY)).isEqualTo(OperatingModel.FRANCHISE);
    }

    @Test
    void anOrganizationCanSwitchBackLater() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty()
                .withChange(OperatingModel.FRANCHISE, TODAY, TODAY)
                .withChange(OperatingModel.DIRECT, TODAY.plusMonths(6), TODAY);

        assertThat(schedule.modelOn(TODAY.plusMonths(3))).isEqualTo(OperatingModel.FRANCHISE);
        assertThat(schedule.modelOn(TODAY.plusMonths(6))).isEqualTo(OperatingModel.DIRECT);
        assertThat(schedule.periods()).hasSize(2);
    }

    @Test
    void aChangeCannotStartInThePast() {
        assertRefused(() -> OperatingModelSchedule.empty().withChange(OperatingModel.FRANCHISE, TODAY.minusDays(1), TODAY),
                OperatingModelException.Kind.INVALID, OperatingModelConstants.ERROR_DATE_IN_PAST);
    }

    @Test
    void aChangeMustStartAfterTheLastScheduledOne() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty()
                .withChange(OperatingModel.FRANCHISE, TODAY.plusDays(10), TODAY);

        assertRefused(() -> schedule.withChange(OperatingModel.DIRECT, TODAY.plusDays(10), TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_DATE_NOT_AFTER_LAST);
        assertRefused(() -> schedule.withChange(OperatingModel.DIRECT, TODAY.plusDays(5), TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_DATE_NOT_AFTER_LAST);
    }

    @Test
    void switchingToTheModelAlreadyInForceIsRefused() {
        assertRefused(() -> OperatingModelSchedule.empty().withChange(OperatingModel.DIRECT, TODAY, TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_SAME_MODEL);
    }

    @Test
    void hybridIsAThirdModelReachableFromAndLeavingToEitherOther() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty()
                .withChange(OperatingModel.HYBRID, TODAY, TODAY)
                .withChange(OperatingModel.FRANCHISE, TODAY.plusMonths(1), TODAY)
                .withChange(OperatingModel.HYBRID, TODAY.plusMonths(2), TODAY)
                .withChange(OperatingModel.DIRECT, TODAY.plusMonths(3), TODAY);

        assertThat(schedule.modelOn(TODAY)).isEqualTo(OperatingModel.HYBRID);
        assertThat(schedule.modelOn(TODAY.plusMonths(1))).isEqualTo(OperatingModel.FRANCHISE);
        assertThat(schedule.modelOn(TODAY.plusMonths(2))).isEqualTo(OperatingModel.HYBRID);
        assertThat(schedule.modelOn(TODAY.plusMonths(3))).isEqualTo(OperatingModel.DIRECT);
        assertThat(schedule.toList().get(0)).containsEntry(OperatingModelConstants.MODEL_FIELD, "HYBRID");
    }

    @Test
    void aChangeMustDifferFromTheModelInForceBeforeItNotJustFromOneOtherModel() {
        OperatingModelSchedule hybrid = OperatingModelSchedule.empty().withChange(OperatingModel.HYBRID, TODAY, TODAY);

        assertRefused(() -> hybrid.withChange(OperatingModel.HYBRID, TODAY.plusDays(1), TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_SAME_MODEL);
        assertRefused(() -> OperatingModelSchedule.empty().withFutureChanges(List.of(
                        new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.plusDays(1)),
                        new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.plusDays(2))), TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_SAME_MODEL);
        // DIRECT to FRANCHISE to DIRECT is not required: DIRECT straight to HYBRID, then HYBRID to FRANCHISE, is fine.
        assertThat(OperatingModelSchedule.empty().withFutureChanges(List.of(
                new OperatingModelPeriod(OperatingModel.HYBRID, TODAY.plusDays(1)),
                new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.plusDays(2))), TODAY).periods()).hasSize(2);
    }

    @Test
    void modelAndDateAreRequired() {
        assertRefused(() -> OperatingModelSchedule.empty().withChange(null, TODAY, TODAY),
                OperatingModelException.Kind.INVALID, OperatingModelConstants.ERROR_MODEL_REQUIRED);
        assertRefused(() -> OperatingModelSchedule.empty().withChange(OperatingModel.FRANCHISE, null, TODAY),
                OperatingModelException.Kind.INVALID, OperatingModelConstants.ERROR_DATE_REQUIRED);
    }

    @Test
    void replacingTheFutureKeepsWhatIsInForceAndDropsWhatHasNotStarted() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty()
                .withChange(OperatingModel.FRANCHISE, TODAY.minusDays(30), TODAY.minusDays(30))
                .withChange(OperatingModel.DIRECT, TODAY.plusDays(5), TODAY);

        OperatingModelSchedule replaced = schedule.withFutureChanges(
                List.of(new OperatingModelPeriod(OperatingModel.DIRECT, TODAY.plusDays(20))), TODAY);

        assertThat(replaced.periods()).containsExactly(
                new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.minusDays(30)),
                new OperatingModelPeriod(OperatingModel.DIRECT, TODAY.plusDays(20)));
    }

    @Test
    void anEmptyRequestCancelsEveryScheduledChangeButNotTheOneInForce() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty()
                .withChange(OperatingModel.FRANCHISE, TODAY, TODAY)
                .withChange(OperatingModel.DIRECT, TODAY.plusDays(5), TODAY);

        assertThat(schedule.withFutureChanges(List.of(), TODAY).periods())
                .containsExactly(new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY));
        assertThat(schedule.withFutureChanges(null, TODAY).periods()).hasSize(1);
    }

    @Test
    void aReplacementCannotRewriteThePast() {
        OperatingModelSchedule schedule = OperatingModelSchedule.empty().withChange(OperatingModel.FRANCHISE, TODAY, TODAY);

        assertRefused(() -> schedule.withFutureChanges(List.of(new OperatingModelPeriod(OperatingModel.DIRECT, TODAY.minusDays(1))), TODAY),
                OperatingModelException.Kind.INVALID, OperatingModelConstants.ERROR_DATE_IN_PAST);
        assertRefused(() -> schedule.withFutureChanges(List.of(new OperatingModelPeriod(OperatingModel.DIRECT, TODAY)), TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_DATE_NOT_AFTER_LAST);
    }

    @Test
    void aReplacementMustBeInOrderAndMustAlternate() {
        assertRefused(() -> OperatingModelSchedule.empty().withFutureChanges(List.of(
                        new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.plusDays(10)),
                        new OperatingModelPeriod(OperatingModel.DIRECT, TODAY.plusDays(2))), TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_DATE_NOT_AFTER_LAST);
        assertRefused(() -> OperatingModelSchedule.empty().withFutureChanges(List.of(
                        new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.plusDays(2)),
                        new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.plusDays(10))), TODAY),
                OperatingModelException.Kind.CONFLICT, OperatingModelConstants.ERROR_SAME_MODEL);
        assertRefused(() -> OperatingModelSchedule.empty().withFutureChanges(java.util.Arrays.asList((OperatingModelPeriod) null), TODAY),
                OperatingModelException.Kind.INVALID, OperatingModelConstants.ERROR_MODEL_REQUIRED);
    }

    @Test
    void storedChangesBuildTheScheduleAndAreWrittenToTheAuditAsPlainValues() {
        OperatingModelSchedule schedule = OperatingModelSchedule.of(List.of(
                new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY),
                new OperatingModelPeriod(OperatingModel.DIRECT, TODAY.plusDays(30))));

        assertThat(schedule.modelOn(TODAY)).isEqualTo(OperatingModel.FRANCHISE);
        assertThat(schedule.toList()).containsExactly(
                Map.of("model", "FRANCHISE", "effectiveFrom", "2026-11-08"),
                Map.of("model", "DIRECT", "effectiveFrom", "2026-12-08"));
        assertThat(OperatingModelSchedule.of(null).periods()).isEmpty();
    }

    @Test
    void storedChangesOutOfOrderFailInsteadOfBeingGuessedAt() {
        assertThatThrownBy(() -> OperatingModelSchedule.of(List.of(
                new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY.plusDays(1)),
                new OperatingModelPeriod(OperatingModel.DIRECT, TODAY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(OperatingModelConstants.ERROR_MALFORMED);
        assertThatThrownBy(() -> OperatingModelSchedule.of(List.of(
                new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY),
                new OperatingModelPeriod(OperatingModel.DIRECT, TODAY))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyChangesAfterADayAreStartingAfterIt() {
        OperatingModelSchedule schedule = OperatingModelSchedule.of(List.of(
                new OperatingModelPeriod(OperatingModel.FRANCHISE, TODAY),
                new OperatingModelPeriod(OperatingModel.DIRECT, TODAY.plusDays(30))));

        assertThat(schedule.startingAfter(TODAY)).containsExactly(new OperatingModelPeriod(OperatingModel.DIRECT, TODAY.plusDays(30)));
    }

    @Test
    void aPeriodNeedsAModelAndADay() {
        assertThatThrownBy(() -> new OperatingModelPeriod(null, TODAY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OperatingModelPeriod(OperatingModel.DIRECT, null)).isInstanceOf(NullPointerException.class);
    }

    private static void assertRefused(Runnable action, OperatingModelException.Kind kind, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(OperatingModelException.class)
                .hasMessage(message)
                .extracting(e -> ((OperatingModelException) e).kind()).isEqualTo(kind);
    }
}
