import { Component, Input, OnChanges } from '@angular/core';
import { BpmnGraph } from '../../workflow.models';
import { GraphLayout, layoutGraph } from '../bpmn-layout';

/** Draws a BPMN graph as SVG (see bpmn-layout.ts for how it is placed). */
@Component({
  selector: 'app-bpmn-graph-view',
  standalone: false,
  templateUrl: './bpmn-graph-view.html',
  styleUrl: './bpmn-graph-view.scss',
})
export class BpmnGraphView implements OnChanges {
  @Input() graph: BpmnGraph | null = null;

  layout: GraphLayout | null = null;

  ngOnChanges(): void {
    this.layout = this.graph && this.graph.nodes.length > 0 ? layoutGraph(this.graph) : null;
  }

  /** Points of a diamond centred in the node's box. */
  diamond(x: number, y: number, w: number, h: number): string {
    return `${x + w / 2},${y} ${x + w},${y + h / 2} ${x + w / 2},${y + h} ${x},${y + h / 2}`;
  }

  /** Gateway marker: X for exclusive, + for parallel / inclusive-style. */
  gatewayMark(type: string): string {
    if (type === 'exclusiveGateway') return '×';
    if (type === 'parallelGateway') return '+';
    return '○';
  }
}
