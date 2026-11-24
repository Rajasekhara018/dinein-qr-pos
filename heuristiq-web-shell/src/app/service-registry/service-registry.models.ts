/** One running instance of a service, as the AdminAPI returns it from the Eureka registry. */
export interface RegistryInstance {
  instanceId: string;
  host?: string;
  ip?: string;
  port?: number;
  /** UP, DOWN, STARTING, OUT_OF_SERVICE or UNKNOWN. */
  status: string;
  healthCheckUrl?: string;
  homePageUrl?: string;
  zone?: string;
  registeredAt?: string;
  upSince?: string;
  lastRenewedAt?: string;
}

/** A service and its registered instances. */
export interface RegistryApp {
  name: string;
  instances: RegistryInstance[];
}

/** AdminAPI's envelope. */
export interface RegistryResponse<T> {
  status: boolean;
  data?: T;
  message?: string;
}
