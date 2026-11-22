import { Component, computed, input } from '@angular/core';

export interface ChartSeries {
  name: string;
  color: string;
}

export interface ChartGroup {
  label: string;
  values: number[];
}

/**
 * Dependency-free grouped bar chart (SVG). Supports negative values with a zero baseline.
 * One value per series in each group.
 */
@Component({
  selector: 'app-bar-chart',
  template: `
    <div class="chart">
      <div class="chart-legend">
        @for (s of series(); track s.name) {
          <span class="legend-item"><span class="legend-swatch" [style.background]="s.color"></span>{{ s.name }}</span>
        }
      </div>
      @if (layout(); as l) {
        <svg [attr.viewBox]="'0 0 ' + l.width + ' ' + l.height" role="img" [attr.aria-label]="ariaLabel()" preserveAspectRatio="none" class="chart-svg">
          @for (t of l.ticks; track t.y) {
            <line [attr.x1]="l.left" [attr.x2]="l.width" [attr.y1]="t.y" [attr.y2]="t.y" class="chart-grid" />
            <text [attr.x]="l.left - 6" [attr.y]="t.y + 4" text-anchor="end" class="chart-axis">{{ t.text }}</text>
          }
          <line [attr.x1]="l.left" [attr.x2]="l.width" [attr.y1]="l.zeroY" [attr.y2]="l.zeroY" class="chart-zero" />
          @for (b of l.bars; track $index; let i = $index) {
            <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" [style.fill]="b.color" [style.animation-delay]="i * 30 + 'ms'" class="chart-bar" rx="4">
              <title>{{ b.title }}</title>
            </rect>
          }
          @for (x of l.labels; track $index) {
            <text [attr.x]="x.x" [attr.y]="l.height - 6" text-anchor="middle" class="chart-axis">{{ x.text }}</text>
          }
        </svg>
      }
    </div>
  `,
  styles: [`
    @keyframes bp-bar-grow { from { transform: scaleY(0); } to { transform: scaleY(1); } }
    .chart-bar {
      transform-box: fill-box; transform-origin: center;
      animation: bp-bar-grow .6s cubic-bezier(.2, .8, .2, 1) both;
    }
    .chart-grid { stroke: var(--border, #e2e8f0); stroke-width: 1; }
    .chart-zero { stroke: var(--muted, #94a3b8); stroke-width: 1; }
    .chart-axis { fill: var(--muted, #64748b); font-size: 11px; }
    @media (prefers-reduced-motion: reduce) {
      .chart-bar { animation: none; }
    }
  `],
})
export class BarChart {
  readonly groups = input.required<ChartGroup[]>();
  readonly series = input.required<ChartSeries[]>();
  readonly ariaLabel = input('Bar chart');
  readonly height = input(240);

  protected readonly layout = computed(() => {
    const groups = this.groups();
    const series = this.series();
    const width = Math.max(groups.length * 56, 560);
    const height = this.height();
    const left = 64;
    const top = 10;
    const bottom = 26;
    const plotH = height - top - bottom;

    const all = groups.flatMap((g) => g.values);
    let max = Math.max(0, ...all);
    let min = Math.min(0, ...all);
    if (max === 0 && min === 0) max = 1;
    const pad = (max - min) * 0.08;
    max += max > 0 ? pad : 0;
    min -= min < 0 ? pad : 0;
    const scale = (v: number) => top + ((max - v) / (max - min)) * plotH;
    const zeroY = scale(0);

    const slot = (width - left) / Math.max(groups.length, 1);
    const groupW = slot * 0.72;
    const barW = groupW / Math.max(series.length, 1);
    const fmt = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });

    const bars = groups.flatMap((g, gi) =>
      g.values.map((v, si) => {
        const y = scale(Math.max(v, 0));
        const h = Math.abs(scale(v) - zeroY);
        return {
          x: left + gi * slot + (slot - groupW) / 2 + si * barW,
          y: v >= 0 ? y : zeroY,
          w: Math.max(barW - 2, 1),
          h: Math.max(h, v === 0 ? 0 : 1),
          color: series[si]?.color ?? 'var(--muted, #94a3b8)',
          title: `${g.label} · ${series[si]?.name ?? ''}: ${v.toLocaleString('en-IN')}`,
        };
      }),
    );

    const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => {
      const v = max - f * (max - min);
      return { y: scale(v), text: fmt.format(v) };
    });

    const every = Math.ceil(groups.length / 12);
    const labels = groups
      .map((g, gi) => ({ x: left + gi * slot + slot / 2, text: g.label, show: gi % every === 0 }))
      .filter((l) => l.show);

    return { width, height, left, zeroY, bars, ticks, labels };
  });
}
