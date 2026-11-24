import { FranchiseApplication, FranchiseDriver, FranchiseMember } from './franchise.models';

/** The deployed application code and the legacy code used by older franchise records. */
export function isDriverApplication(code: string): boolean {
  return code === 'BESTRIDE_RIDER_APP' || code === 'RIDER';
}

/**
 * The places an application of a franchise uses, as the members screens show them: its ACTIVE drivers for the driver application
 * (counted live by Rider_FranchiseService), its agents for any other.
 */
export function placesUsed(application: Pick<FranchiseApplication, 'applicationCode' | 'agentCount' | 'driverCount'>): number {
  return (isDriverApplication(application.applicationCode) ? application.driverCount : application.agentCount) ?? 0;
}

/** A driver of a franchise as a row of the members tables (an ACTIVE DRIVER member of the driver application). */
export function driverAsMember(driver: FranchiseDriver, applicationCode: string): FranchiseMember {
  return {
    memberId: driver.memberId,
    userId: driver.userId,
    fullName: driver.name,
    email: driver.email ?? '',
    phone: driver.phone,
    applicationCode,
    memberType: 'DRIVER',
    status: 'ACTIVE',
    roleCode: null,
    onboardedAt: driver.onboardedAt ?? '',
  };
}
