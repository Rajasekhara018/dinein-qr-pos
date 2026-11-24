export interface PrivilegeRow {
  privilegeCode: string;
  service: string;
  name: string;
}
export interface FeatureRule {
  key: string;
  prefixes: string[];
}
export interface AppRule {
  key: string;
  sources: Array<{ codePrefix: string; service?: string; nameStarts?: string[] }>;
  features: FeatureRule[];
}
export function codePrefix(privilegeCode: string): string;
export function buildAppFeatures(
  rules: { apps: AppRule[] },
  privileges: PrivilegeRow[],
): { apps: Record<string, Array<{ key: string; privileges: string[] }>>; errors: string[] };
export function renderGenerated(apps: Record<string, Array<{ key: string; privileges: string[] }>>): string;
