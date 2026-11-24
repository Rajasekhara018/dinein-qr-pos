/**
 * Starting points for a new workflow. Each builds a complete, deployable process: the steps are unassigned and have no
 * deadline until you set them in "Step settings". Every request is completed with decision = APPROVED or REJECTED, like
 * the bundled flows, so Onboarding's Approve / Reject buttons work with any of them.
 */

export interface WorkflowTemplate {
  id: string;
  /** Translation keys: workflow.definitions.templates.<id>.title / .description */
  titleKey: string;
  descriptionKey: string;
  build(key: string, name: string): string;
}

const CALLBACK = '<extensionElements><flowable:executionListener event="end" delegateExpression="${workflowCallbackListener}"/></extensionElements>';

function escapeXml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function wrap(key: string, name: string, comment: string, body: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:rider="http://rider.com/workflow/extensions"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             targetNamespace="http://rider.com/workflow">

    <!--
${comment}
    -->
    <process id="${key}" name="${escapeXml(name)}" isExecutable="true">
${body}
    </process>
</definitions>
`;
}

const cond = (variable: string, value: string) =>
  `<conditionExpression xsi:type="tFormalExpression"><![CDATA[\${${variable} == '${value}'}]]></conditionExpression>`;

const SINGLE: WorkflowTemplate = {
  id: 'single',
  titleKey: 'workflow.definitions.templates.single.title',
  descriptionKey: 'workflow.definitions.templates.single.description',
  build: (key, name) => wrap(key, name,
    `      One review step: Submitted -> "Review" -> Approved or Rejected.\n      The step is completed with the variable "decision" = APPROVED or REJECTED (and an optional "reason").`,
    `        <startEvent id="startEvent" name="Submitted"/>
        <userTask id="review" name="Review"/>
        <exclusiveGateway id="decision" name="Approved?"/>
        <endEvent id="endApproved" name="Approved">${CALLBACK}</endEvent>
        <endEvent id="endRejected" name="Rejected">${CALLBACK}</endEvent>
        <sequenceFlow id="flowStart" sourceRef="startEvent" targetRef="review"/>
        <sequenceFlow id="flowReviewed" sourceRef="review" targetRef="decision"/>
        <sequenceFlow id="flowApproved" name="Approved" sourceRef="decision" targetRef="endApproved">${cond('decision', 'APPROVED')}</sequenceFlow>
        <sequenceFlow id="flowRejected" name="Rejected" sourceRef="decision" targetRef="endRejected">${cond('decision', 'REJECTED')}</sequenceFlow>`),
};

const TWO_STEP: WorkflowTemplate = {
  id: 'twoStep',
  titleKey: 'workflow.definitions.templates.twoStep.title',
  descriptionKey: 'workflow.definitions.templates.twoStep.description',
  build: (key, name) => wrap(key, name,
    `      Two reviews in order: "Verify documents" -> "Final approval". A rejection at either step ends the request as Rejected.`,
    `        <startEvent id="startEvent" name="Submitted"/>
        <userTask id="documentCheck" name="Verify documents"/>
        <exclusiveGateway id="documentDecision" name="Documents OK?"/>
        <userTask id="finalApproval" name="Final approval"/>
        <exclusiveGateway id="finalDecision" name="Approve?"/>
        <endEvent id="endApproved" name="Approved">${CALLBACK}</endEvent>
        <endEvent id="endRejected" name="Rejected">${CALLBACK}</endEvent>
        <sequenceFlow id="flowStart" sourceRef="startEvent" targetRef="documentCheck"/>
        <sequenceFlow id="flowDocumentsDecided" sourceRef="documentCheck" targetRef="documentDecision"/>
        <sequenceFlow id="flowDocumentsOk" name="Approved" sourceRef="documentDecision" targetRef="finalApproval">${cond('decision', 'APPROVED')}</sequenceFlow>
        <sequenceFlow id="flowDocumentsRejected" name="Rejected" sourceRef="documentDecision" targetRef="endRejected">${cond('decision', 'REJECTED')}</sequenceFlow>
        <sequenceFlow id="flowFinalDecided" sourceRef="finalApproval" targetRef="finalDecision"/>
        <sequenceFlow id="flowFinalApproved" name="Approved" sourceRef="finalDecision" targetRef="endApproved">${cond('decision', 'APPROVED')}</sequenceFlow>
        <sequenceFlow id="flowFinalRejected" name="Rejected" sourceRef="finalDecision" targetRef="endRejected">${cond('decision', 'REJECTED')}</sequenceFlow>`),
};

const PARALLEL: WorkflowTemplate = {
  id: 'parallel',
  titleKey: 'workflow.definitions.templates.parallel.title',
  descriptionKey: 'workflow.definitions.templates.parallel.description',
  build: (key, name) => wrap(key, name,
    `      Two reviews at the same time ("Legal check" and "Finance check"). The request is approved only when BOTH approve;
      if either rejects, it is rejected. Each step's decision is kept as decision_<stepId> (decision_legalCheck,
      decision_financeCheck), and the end events say how it ended (rider:outcome), because the last step to finish
      would otherwise decide. Give each step its own roles or people in "Step settings".`,
    `        <startEvent id="startEvent" name="Submitted"/>
        <parallelGateway id="fork" name="Review together"/>
        <userTask id="legalCheck" name="Legal check"/>
        <userTask id="financeCheck" name="Finance check"/>
        <parallelGateway id="join" name="Both done"/>
        <exclusiveGateway id="verdict" name="Both approved?"/>
        <endEvent id="endApproved" name="Approved" rider:outcome="APPROVED">${CALLBACK}</endEvent>
        <endEvent id="endRejected" name="Rejected" rider:outcome="REJECTED">${CALLBACK}</endEvent>
        <sequenceFlow id="flowStart" sourceRef="startEvent" targetRef="fork"/>
        <sequenceFlow id="flowToLegal" sourceRef="fork" targetRef="legalCheck"/>
        <sequenceFlow id="flowToFinance" sourceRef="fork" targetRef="financeCheck"/>
        <sequenceFlow id="flowLegalDone" sourceRef="legalCheck" targetRef="join"/>
        <sequenceFlow id="flowFinanceDone" sourceRef="financeCheck" targetRef="join"/>
        <sequenceFlow id="flowJoined" sourceRef="join" targetRef="verdict"/>
        <sequenceFlow id="flowAllApproved" name="Both approved" sourceRef="verdict" targetRef="endApproved">
            <conditionExpression xsi:type="tFormalExpression"><![CDATA[\${decision_legalCheck == 'APPROVED' && decision_financeCheck == 'APPROVED'}]]></conditionExpression>
        </sequenceFlow>
        <sequenceFlow id="flowAnyRejected" name="Either rejected" sourceRef="verdict" targetRef="endRejected">
            <conditionExpression xsi:type="tFormalExpression"><![CDATA[\${decision_legalCheck != 'APPROVED' || decision_financeCheck != 'APPROVED'}]]></conditionExpression>
        </sequenceFlow>`),
};

const QUORUM: WorkflowTemplate = {
  id: 'quorum',
  titleKey: 'workflow.definitions.templates.quorum.title',
  descriptionKey: 'workflow.definitions.templates.quorum.description',
  build: (key, name) => wrap(key, name,
    `      Three reviewers look at the same time ("Reviewer one", "Reviewer two", "Reviewer three"). When all three have
      decided, the request is approved if at least TWO approved, and rejected otherwise. Each step's decision is kept as
      decision_<stepId>, and the end events say how it ended (rider:outcome). To change the rule, edit the two conditions
      on the "Majority?" gateway. Give each reviewer their own roles or people in "Step settings".`,
    `        <startEvent id="startEvent" name="Submitted"/>
        <parallelGateway id="fork" name="Review together"/>
        <userTask id="reviewerOne" name="Reviewer one"/>
        <userTask id="reviewerTwo" name="Reviewer two"/>
        <userTask id="reviewerThree" name="Reviewer three"/>
        <parallelGateway id="join" name="All three done"/>
        <exclusiveGateway id="majority" name="Majority?"/>
        <endEvent id="endApproved" name="Approved" rider:outcome="APPROVED">${CALLBACK}</endEvent>
        <endEvent id="endRejected" name="Rejected" rider:outcome="REJECTED">${CALLBACK}</endEvent>
        <sequenceFlow id="flowStart" sourceRef="startEvent" targetRef="fork"/>
        <sequenceFlow id="flowToOne" sourceRef="fork" targetRef="reviewerOne"/>
        <sequenceFlow id="flowToTwo" sourceRef="fork" targetRef="reviewerTwo"/>
        <sequenceFlow id="flowToThree" sourceRef="fork" targetRef="reviewerThree"/>
        <sequenceFlow id="flowOneDone" sourceRef="reviewerOne" targetRef="join"/>
        <sequenceFlow id="flowTwoDone" sourceRef="reviewerTwo" targetRef="join"/>
        <sequenceFlow id="flowThreeDone" sourceRef="reviewerThree" targetRef="join"/>
        <sequenceFlow id="flowJoined" sourceRef="join" targetRef="majority"/>
        <sequenceFlow id="flowAtLeastTwo" name="2 or more approved" sourceRef="majority" targetRef="endApproved">
            <conditionExpression xsi:type="tFormalExpression"><![CDATA[\${(decision_reviewerOne == 'APPROVED' && decision_reviewerTwo == 'APPROVED') || (decision_reviewerOne == 'APPROVED' && decision_reviewerThree == 'APPROVED') || (decision_reviewerTwo == 'APPROVED' && decision_reviewerThree == 'APPROVED')}]]></conditionExpression>
        </sequenceFlow>
        <sequenceFlow id="flowFewerThanTwo" name="Fewer than 2" sourceRef="majority" targetRef="endRejected">
            <conditionExpression xsi:type="tFormalExpression"><![CDATA[\${!((decision_reviewerOne == 'APPROVED' && decision_reviewerTwo == 'APPROVED') || (decision_reviewerOne == 'APPROVED' && decision_reviewerThree == 'APPROVED') || (decision_reviewerTwo == 'APPROVED' && decision_reviewerThree == 'APPROVED'))}]]></conditionExpression>
        </sequenceFlow>`),
};

const BY_ANSWER: WorkflowTemplate = {
  id: 'byAnswer',
  titleKey: 'workflow.definitions.templates.byAnswer.title',
  descriptionKey: 'workflow.definitions.templates.byAnswer.description',
  build: (key, name) => wrap(key, name,
    `      Routes on the applicant's form answers. A vehicle that is an SUV or has more than 6 seats gets an extra
      "Vehicle inspection" first; everything else goes straight to "Final review". Change the condition to suit your form.
      The answers must be listed in the Onboarding service setting onboarding.workflow.answer-variables
      (here: vehicleType,seats); each is passed to the flow as answer_<fieldKey>.`,
    `        <startEvent id="startEvent" name="Submitted"/>
        <exclusiveGateway id="route" name="Needs inspection?"/>
        <userTask id="inspection" name="Vehicle inspection"/>
        <userTask id="finalReview" name="Final review"/>
        <exclusiveGateway id="decision" name="Approved?"/>
        <endEvent id="endApproved" name="Approved">${CALLBACK}</endEvent>
        <endEvent id="endRejected" name="Rejected">${CALLBACK}</endEvent>
        <sequenceFlow id="flowStart" sourceRef="startEvent" targetRef="route"/>
        <sequenceFlow id="flowInspect" name="SUV or 7+ seats" sourceRef="route" targetRef="inspection">
            <conditionExpression xsi:type="tFormalExpression"><![CDATA[\${answer_vehicleType == 'SUV' || answer_seats > 6}]]></conditionExpression>
        </sequenceFlow>
        <sequenceFlow id="flowStraight" name="Otherwise" sourceRef="route" targetRef="finalReview">
            <conditionExpression xsi:type="tFormalExpression"><![CDATA[\${answer_vehicleType != 'SUV' && answer_seats <= 6}]]></conditionExpression>
        </sequenceFlow>
        <sequenceFlow id="flowInspected" sourceRef="inspection" targetRef="finalReview"/>
        <sequenceFlow id="flowReviewed" sourceRef="finalReview" targetRef="decision"/>
        <sequenceFlow id="flowApproved" name="Approved" sourceRef="decision" targetRef="endApproved">${cond('decision', 'APPROVED')}</sequenceFlow>
        <sequenceFlow id="flowRejected" name="Rejected" sourceRef="decision" targetRef="endRejected">${cond('decision', 'REJECTED')}</sequenceFlow>`),
};

export const WORKFLOW_TEMPLATES: readonly WorkflowTemplate[] = [SINGLE, TWO_STEP, PARALLEL, QUORUM, BY_ANSWER];

/** A process key the service accepts: starts with a letter, then letters, digits, _ - . (max 64). */
export const PROCESS_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/;
