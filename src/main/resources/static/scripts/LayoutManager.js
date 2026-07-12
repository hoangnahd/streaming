/**
 * LayoutManager
 * -------------
 * Purely about arrangement: grid vs. hero (click-to-focus) mode, and the
 * participant-count badge. Does not create or destroy participant cards —
 * that's ParticipantManager's job. This just re-parents existing cards.
 */
export class LayoutManager {
  constructor({ gridContainer, countBadge }) {
    this.gridContainer = gridContainer;
    this.countBadge = countBadge;
    this.focusedParticipantId = null;
  }

  toggleFocus(participantId) {
    this.focusedParticipantId = this.focusedParticipantId === participantId ? null : participantId;
    this.rebalance();
  }

  rebalance(participantCount) {
    const allCardsBefore = this.gridContainer.querySelectorAll('[data-participant-id]');
    const totalPeers = participantCount ?? allCardsBefore.length;

    this.countBadge.innerText = `${totalPeers} Participant${totalPeers > 1 ? 's' : ''} Active`;
    this.gridContainer.className = 'w-full h-full max-w-7xl transition-all duration-300 gap-4';

    const allCards = this.gridContainer.querySelectorAll('[data-participant-id]');
    allCards.forEach((card) => {
      card.className =
        'btn-transition relative w-full bg-zinc-900/50 border border-zinc-800/80 rounded-2xl overflow-hidden shadow-xl flex items-center justify-center cursor-pointer group hover:border-zinc-700';
    });

    if (this.focusedParticipantId && this._hasParticipant(this.focusedParticipantId)) {
      this._layoutHero(allCards);
      return;
    }

    this._layoutGrid(allCards, totalPeers);
  }

  // --- internals -----------------------------------------------------

  _hasParticipant(id) {
    return !!this.gridContainer.querySelector(`[data-participant-id="${id}"]`);
  }

  _layoutHero(allCards) {
    this.gridContainer.classList.add('flex', 'flex-col', 'md:flex-row', 'items-stretch');

    let strip = document.getElementById('thumbStripWindow');
    if (!strip) {
      strip = document.createElement('div');
      strip.id = 'thumbStripWindow';
      strip.className = 'flex md:flex-col gap-3 shrink-0 w-full md:w-64 h-32 md:h-full overflow-x-auto md:overflow-y-auto p-1';
      this.gridContainer.appendChild(strip);
    } else {
      this.gridContainer.appendChild(strip);
    }

    allCards.forEach((card) => {
      const pid = card.getAttribute('data-participant-id');
      if (pid === this.focusedParticipantId) {
        card.classList.add('flex-1', 'h-full', 'border-indigo-500/50', 'shadow-indigo-950/20');
        this.gridContainer.insertBefore(card, strip);
      } else {
        card.classList.add('w-44', 'md:w-full', 'h-full', 'md:h-36', 'shrink-0');
        strip.appendChild(card);
      }
    });
  }

  _layoutGrid(allCards, totalPeers) {
    document.getElementById('thumbStripWindow')?.remove();
    allCards.forEach((card) => this.gridContainer.appendChild(card));

    this.gridContainer.classList.add('grid');
    if (totalPeers === 1) {
      this.gridContainer.classList.add('grid-cols-1', 'max-w-3xl', 'aspect-video');
    } else if (totalPeers === 2) {
      this.gridContainer.classList.add('grid-cols-1', 'md:grid-cols-2', 'max-w-5xl', 'h-auto', 'aspect-video');
    } else if (totalPeers <= 4) {
      this.gridContainer.classList.add('grid-cols-2', 'h-full');
    } else {
      this.gridContainer.classList.add('grid-cols-2', 'md:grid-cols-3', 'h-full');
    }
  }
}