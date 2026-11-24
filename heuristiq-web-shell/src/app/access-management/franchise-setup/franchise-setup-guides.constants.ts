import { AdminFormGuide } from '../admin-form.types';
import { FranchiseSetupStepId } from './franchise-setup.constants';

/** Where the reader's choice to fold the step guides away is kept (per browser, one choice for every step). */
export const FRANCHISE_SETUP_GUIDE_STORAGE_KEY = 'rou.franchiseSetup.guide.folded';

const G = 'accessManagement.franchises.setup.guides';
const FR = 'accessManagement.franchises';
const ON = 'franchiseOnboarding';
const LP = 'legalProfile';

/** The guide of each setup step: what it is for, what each part means and an example (translation keys). */
export const FRANCHISE_SETUP_GUIDES: Readonly<Record<FranchiseSetupStepId, AdminFormGuide>> = {
  details: {
    title: `${G}.title`,
    intro: `${G}.details.intro`,
    sections: [
      {
        title: `${G}.fill`,
        items: [
          { term: `${FR}.owner`, text: `${G}.details.owner` },
          { term: `${FR}.application`, text: `${G}.details.application` },
          { term: `${FR}.place`, text: `${G}.details.place` },
          { term: `${FR}.agentLimit`, text: `${G}.details.agentLimit` },
          { term: `${FR}.fee`, text: `${G}.details.fee` },
          { term: `${FR}.package.column`, text: `${G}.details.package` },
          { term: `${FR}.status`, text: `${G}.details.status` },
        ],
      },
      { title: `${G}.check`, text: `${G}.details.check` },
      { title: `${G}.example`, text: `${G}.details.example` },
    ],
  },
  legal: {
    title: `${G}.title`,
    intro: `${G}.legal.intro`,
    sections: [
      {
        title: `${G}.fill`,
        items: [
          { term: `${LP}.entityTypeLabel`, text: `${G}.legal.entityType` },
          { term: `${LP}.legalNameLabel`, text: `${G}.legal.legalName` },
          { term: `${LP}.tradingNameLabel`, text: `${G}.legal.tradingName` },
          { term: `${LP}.registrationNumberLabel`, text: `${G}.legal.registration` },
          { term: `${LP}.taxSection`, text: `${G}.legal.tax` },
          { term: `${LP}.addressSection`, text: `${G}.legal.addresses` },
        ],
      },
      { title: `${G}.example`, text: `${G}.legal.example` },
    ],
  },
  kyc: {
    title: `${G}.title`,
    intro: `${G}.kyc.intro`,
    sections: [
      {
        title: `${G}.kyc.typesTitle`,
        items: [
          { term: `${ON}.documentTypes.IDENTITY_PROOF`, text: `${G}.kyc.identity` },
          { term: `${ON}.documentTypes.ADDRESS_PROOF`, text: `${G}.kyc.address` },
          { term: `${ON}.documentTypes.BUSINESS_REGISTRATION`, text: `${G}.kyc.business` },
          { term: `${ON}.documentTypes.TAX_CERTIFICATE`, text: `${G}.kyc.tax` },
        ],
      },
      {
        title: `${G}.kyc.statesTitle`,
        items: [
          { term: `${ON}.state.SUBMITTED`, text: `${G}.kyc.submitted` },
          { term: `${ON}.state.VERIFIED`, text: `${G}.kyc.verified` },
          { term: `${ON}.state.REJECTED`, text: `${G}.kyc.rejected` },
        ],
      },
      { title: `${G}.example`, text: `${G}.kyc.example` },
    ],
  },
  bank: {
    title: `${G}.title`,
    intro: `${G}.bank.intro`,
    sections: [
      {
        title: `${G}.fill`,
        items: [
          { term: `${ON}.bank.holderName`, text: `${G}.bank.holder` },
          { term: `${ON}.bank.accountNumber`, text: `${G}.bank.account` },
          { term: `${ON}.bank.routing.IFSC`, text: `${G}.bank.routing` },
          { term: `${ON}.bank.proof`, text: `${G}.bank.proof` },
        ],
      },
      { title: `${G}.check`, text: `${G}.bank.review` },
      { title: `${G}.example`, text: `${G}.bank.example` },
    ],
  },
  agreement: {
    title: `${G}.title`,
    intro: `${G}.agreement.intro`,
    sections: [
      {
        title: `${G}.fill`,
        items: [
          { term: `${ON}.agreement.number`, text: `${G}.agreement.number` },
          { term: `${ON}.agreement.startsOn`, text: `${G}.agreement.dates` },
          { term: `${ON}.agreement.renewal`, text: `${G}.agreement.renewal` },
          { term: `${ON}.agreement.exclusive`, text: `${G}.agreement.exclusive` },
          { term: `${ON}.agreement.deposit`, text: `${G}.agreement.deposit` },
          { term: `${ON}.agreement.signedCopy`, text: `${G}.agreement.signedCopy` },
        ],
      },
      { title: `${G}.example`, text: `${G}.agreement.example` },
    ],
  },
  activation: {
    title: `${G}.title`,
    intro: `${G}.activation.intro`,
    sections: [
      {
        title: `${ON}.checklist.title`,
        items: [
          { term: `${ON}.checklist.legalProfile`, text: `${G}.activation.legal` },
          { term: `${ON}.checklist.kyc`, text: `${G}.activation.kyc` },
          { term: `${ON}.checklist.bankAccount`, text: `${G}.activation.bank` },
          { term: `${ON}.checklist.agreement`, text: `${G}.activation.agreement` },
        ],
      },
      { title: `${ON}.activate.button`, text: `${G}.activation.activate` },
      { title: `${G}.example`, text: `${G}.activation.example` },
    ],
  },
};
