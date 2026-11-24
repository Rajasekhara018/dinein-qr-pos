import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  ViewEncapsulation,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { HqSharedLibModule } from 'hq-shared-lib';
import { Subscription } from 'rxjs';
import { LanguageService, SupportedLanguage } from '../shared/services/language-service';
import { PublicTrackMapFactory } from './public-track-map';
import { PUBLIC_TRACK_META_TAGS, PUBLIC_TRACK_STATUS_LABEL_KEYS } from './public-track.constants';
import { formatClockTime, isBeforePickup, readShareToken, toMapView } from './public-track.helpers';
import { PublicTrackMap, PublicTrackResponse, PublicTrackState, PublicTrackUpdate } from './public-track.models';
import { PublicTrackService } from './public-track.service';

/**
 * Public live-trip page (`/track#<token>`), opened from a link the rider shared. No sign-in and no session: the token
 * comes from the URL fragment (never sent to the server in the URL) and every read carries it in `X-Share-Token`.
 * Shows only what the API returns (driver first name and photo, vehicle, car position, pickup/drop, ETA). The token is
 * never logged.
 */
@Component({
  selector: 'app-public-track',
  standalone: true,
  imports: [TranslatePipe, FormsModule, HqSharedLibModule],
  templateUrl: './public-track.html',
  styleUrl: './public-track.scss',
  // Leaflet builds its own DOM inside the map host, so its stylesheet (imported in the scss) must not be scoped.
  encapsulation: ViewEncapsulation.None,
})
export class PublicTrack implements OnInit, AfterViewInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(PublicTrackService);
  private readonly mapFactory = inject(PublicTrackMapFactory);
  private readonly translate = inject(TranslateService);
  private readonly languageService = inject(LanguageService);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('mapHost') private mapHost?: ElementRef<HTMLElement>;

  state: PublicTrackState = 'loading';
  data: PublicTrackResponse | null = null;
  /** The last read failed: the details shown are from the previous answer. */
  stale = false;
  /** The map could not be loaded (the trip details are still shown). */
  mapFailed = false;
  photoFailed = false;
  /** When the page last received an answer (used when the API gives no location time). */
  receivedAt: string | null = null;
  readonly languages: readonly SupportedLanguage[] = this.languageService.languages;

  private token: string | null = null;
  private watchSub: Subscription | null = null;
  private langSub: Subscription | null = null;
  private map: PublicTrackMap | null = null;
  private mapPromise: Promise<PublicTrackMap | null> | null = null;
  private fitted = false;
  private destroyed = false;
  private previousTitle = '';

  ngOnInit(): void {
    this.languageService.useBrowserLanguage();
    PUBLIC_TRACK_META_TAGS.forEach((tag) => this.meta.updateTag(tag));
    this.previousTitle = this.title.getTitle();
    this.applyTitle();
    this.langSub = this.translate.onLangChange.subscribe(() => this.applyTitle());

    this.token = readShareToken(this.route.snapshot.fragment);
    if (!this.token) {
      this.state = 'invalid';
      return;
    }
    this.watchSub = this.service.watch(this.token).subscribe((update) => this.apply(update));
  }

  ngAfterViewInit(): void {
    if (this.token && this.state !== 'invalid') this.ensureMap();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.watchSub?.unsubscribe();
    this.langSub?.unsubscribe();
    this.map?.destroy();
    this.map = null;
    PUBLIC_TRACK_META_TAGS.forEach((tag) => this.meta.removeTag(`name="${tag.name}"`));
    this.title.setTitle(this.previousTitle);
  }

  get currentLanguage(): string {
    return this.languageService.currentLanguage;
  }

  onLanguageChange(code: string): void {
    this.languageService.setLanguage(code);
  }

  /** Zooms back to the car, the pickup and the drop. */
  recenter(): void {
    if (this.map && this.data) this.map.update(toMapView(this.data), true);
  }

  onPhotoError(): void {
    this.photoFailed = true;
  }

  get statusLabel(): string {
    const status = this.data?.status ?? '';
    const key = PUBLIC_TRACK_STATUS_LABEL_KEYS[status.toUpperCase()];
    return key ? this.translate.instant(key) : status;
  }

  /** ETA sentence, or '' when the API gives none. */
  get etaText(): string {
    const minutes = this.data?.etaMinutes;
    if (minutes === null || minutes === undefined || !Number.isFinite(minutes) || minutes < 0) return '';
    if (minutes === 0) return this.translate.instant('publicTrack.eta.now');
    const key = isBeforePickup(this.data?.status) ? 'publicTrack.eta.toPickup' : 'publicTrack.eta.toDrop';
    return this.translate.instant(key, { minutes: Math.round(minutes) });
  }

  get lastUpdatedText(): string {
    return formatClockTime(this.data?.locationUpdatedAt || this.receivedAt, this.currentLanguage);
  }

  get hasLocation(): boolean {
    return !!this.data && toMapView(this.data).car !== null;
  }

  get showMap(): boolean {
    return this.state === 'live' && !this.mapFailed;
  }

  private apply(update: PublicTrackUpdate): void {
    switch (update.kind) {
      case 'live':
        this.data = update.data;
        this.state = 'live';
        this.stale = false;
        this.receivedAt = new Date().toISOString();
        this.cdr.detectChanges();
        void this.drawMap();
        break;
      case 'ended':
        this.state = 'ended';
        this.data = null;
        this.stale = false;
        break;
      case 'invalid':
        this.state = 'invalid';
        this.data = null;
        this.stale = false;
        break;
      case 'unavailable':
        // Keep showing the last answer, marked as not current; with none yet, show the outage state.
        if (this.data) {
          this.stale = true;
        } else {
          this.state = 'unavailable';
        }
        break;
    }
    this.cdr.detectChanges();
  }

  private ensureMap(): Promise<PublicTrackMap | null> {
    if (!this.mapPromise) {
      const host = this.mapHost?.nativeElement;
      this.mapPromise = host
        ? this.mapFactory.create(host).then(
            (map) => {
              if (this.destroyed) {
                map.destroy();
                return null;
              }
              this.map = map;
              return map;
            },
            () => {
              this.mapFailed = true;
              this.cdr.detectChanges();
              return null;
            },
          )
        : Promise.resolve(null);
    }
    return this.mapPromise;
  }

  private async drawMap(): Promise<void> {
    const map = await this.ensureMap();
    if (!map || !this.data || this.state !== 'live') return;
    const view = toMapView(this.data);
    map.invalidateSize();
    const fit = !this.fitted && (view.car !== null || view.pickup !== null || view.drop !== null);
    map.update(view, fit);
    if (fit) this.fitted = true;
  }

  private applyTitle(): void {
    this.title.setTitle(this.translate.instant('publicTrack.pageTitle'));
  }
}
