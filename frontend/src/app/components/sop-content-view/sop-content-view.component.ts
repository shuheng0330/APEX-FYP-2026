import { Component, Input } from '@angular/core';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { RichContent } from '../../models/sop.model';
import { environment } from '../../../environments/environment';

/**
 * Read-only view of a module's training material: the rich content JSON (summary, objectives,
 * tools, content blocks, key terms), or the plain text of older modules. Used by the trainer's
 * material review and by learners in the Learning Portal.
 */
@Component({
  selector: 'app-sop-content-view',
  standalone: true,
  imports: [NzIconModule, NzTagModule],
  templateUrl: './sop-content-view.component.html',
  styleUrls: ['./sop-content-view.component.scss']
})
export class SopContentViewComponent {
  raw = '';
  rich: RichContent | null = null;

  /** The module content: rich content JSON, or plain text. */
  @Input() set content(value: string | null | undefined) {
    this.raw = value ?? '';
    this.rich = parseRichContent(this.raw);
  }

  /** Reviewers are also told when the material was generated offline instead of by the AI. */
  @Input() reviewer = false;

  /** Resolve a stored media path to a URL the browser can load. */
  mediaSrc(url?: string): string {
    if (!url) { return ''; }
    if (/^https?:\/\//i.test(url)) { return url; }
    // Stored paths are API-relative ("/api/sop/media/..."); apiBaseUrl already ends in "/api".
    return environment.apiBaseUrl.replace(/\/api$/, '') + url;
  }
}

/** Parses module content as rich content, or returns null for plain text. */
export function parseRichContent(content: string): RichContent | null {
  try {
    const parsed = JSON.parse(content);
    if (parsed && typeof parsed === 'object' && 'sections' in parsed) {
      return parsed as RichContent;
    }
  } catch { /* plain text */ }
  return null;
}
