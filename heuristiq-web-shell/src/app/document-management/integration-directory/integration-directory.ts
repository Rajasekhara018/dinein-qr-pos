import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { HqSharedLibModule } from 'hq-shared-lib';
import { environment } from '../../../environments/environment';

interface Connection {
  provider: string;
  baseUrl: string;
  enabled: boolean;
  timeoutSeconds: number;
  credentialHeaders: Record<string, string>;
  methods: Record<string, unknown>;
}
interface Envelope<T> {
  status: boolean;
  data: T;
  message?: string;
}

@Component({
  selector: 'app-integration-directory',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslateModule, HqSharedLibModule],
  templateUrl: './integration-directory.html',
  styleUrl: './integration-directory.scss',
})
export class IntegrationDirectoryPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly base = `${environment.gatewayUrl}/v1/onboarding/admin/integration-directory`;
  connections: Connection[] = [];
  selected: Connection | null = null;
  headersJson = '{}';
  methodsJson = '{}';
  busy = false;
  error = '';
  saved = false;
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    this.busy = true;
    this.error = '';
    forkJoin([this.read<Connection[]>(''), this.read<Connection[]>('/templates')])
      .pipe(
        finalize(() => {
          this.busy = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: ([stored, templates]) => {
          this.connections = templates.map(
            (template) => stored.find((c) => c.provider === template.provider) ?? template,
          );
          if (this.connections.length) this.select(this.connections[0]);
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'integrationDirectory.loadFailed';
        },
      });
  }
  select(connection: Connection): void {
    this.selected = structuredClone(connection);
    this.headersJson = JSON.stringify(connection.credentialHeaders, null, 2);
    this.methodsJson = JSON.stringify(connection.methods, null, 2);
    this.saved = false;
    this.error = '';
  }
  save(): void {
    if (!this.selected || this.busy) return;
    let body: Connection;
    try {
      body = {
        ...this.selected,
        credentialHeaders: JSON.parse(this.headersJson),
        methods: JSON.parse(this.methodsJson),
      };
    } catch {
      this.error = 'integrationDirectory.invalidJson';
      return;
    }
    this.busy = true;
    this.error = '';
    this.saved = false;
    this.http
      .put<Envelope<Connection>>(`${this.base}/${encodeURIComponent(body.provider)}`, body)
      .pipe(
        finalize(() => {
          this.busy = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (response) => {
          if (!response.status) {
            this.error = response.message ?? 'integrationDirectory.saveFailed';
            return;
          }
          this.connections = this.connections.map((c) =>
            c.provider === body.provider ? response.data : c,
          );
          this.saved = true;
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'integrationDirectory.saveFailed';
        },
      });
  }
  private read<T>(suffix: string) {
    return this.http.get<Envelope<T>>(this.base + suffix).pipe(
      map((response) => {
        if (!response.status) throw new Error(response.message);
        return response.data;
      }),
    );
  }
}
