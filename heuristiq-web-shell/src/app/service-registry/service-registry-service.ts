import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { RegistryApp, RegistryResponse } from './service-registry.models';

/** Reads the service registry through the AdminAPI (GET {adminApiUrl}/registry/apps). */
@Injectable({ providedIn: 'root' })
export class ServiceRegistryService {
  private readonly base = `${environment.adminApiUrl}/registry`;

  constructor(private http: HttpClient) {}

  applications(): Observable<RegistryApp[]> {
    return this.http.get<RegistryResponse<RegistryApp[]>>(`${this.base}/apps`).pipe(
      map((res) => {
        if (res && res.status === false) {
          throw { error: { message: res.message } };
        }
        return res?.data ?? [];
      }),
    );
  }
}
