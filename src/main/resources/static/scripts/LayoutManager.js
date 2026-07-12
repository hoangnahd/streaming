/**
 * Pure view layer: arranges whatever [data-participant-id] cards currently
 * exist in gridContainer (this includes the static local-video card already
 * in the Thymeleaf template, plus whatever ParticipantManager has added/removed).
 * It has no opinion on WHO the participants are — it just re-reads the DOM
 * each time it's told something changed.
 */
export class LayoutManager {
  constructor({ gridContainer, countBadge, participantManager }) {
    this.gridContainer = gridContainer;
    this.countBadge = countBadge;
    this.focusedId = null;

    participantManager.addEventListener('changed', () => this.rebalance());
    participantManager.addEventListener('card-clicked', (e) => this.toggleFocus(e.detail.id));
  }

  toggleFocus(id) {
    this.focusedId = this.focusedId === id ? null : id;
    this.rebalance();
  }

  rebalance() {
    const cards = [...this.gridContainer.querySelectorAll('[data-participant-id]')];
    const total = cards.length;
    this.countBadge.innerText = `${total} Participant${total > 1 ? 's' : ''} Active`;

    this.gridContainer.className = 'w-full h-full max-w-7xl transition-all duration-300 gap-4';
    cards.forEach((card) => { card.className = 'participant-card'; });

    const focusedCard = cards.find((c) => c.getAttribute('data-participant-id') === this.focusedId);
    if (focusedCard) {
      this._applyHeroLayout(cards);
      return;
    }

    this._applyGridLayout(cards, total);
  }

  _applyHeroLayout(cards) {
    this.gridContainer.classList.add('flex', 'flex-col', 'md:flex-row', 'items-stretch');

    let strip = document.getElementById('thumbStripWindow');
    if (!strip) {
      strip = document.createElement('div');
      strip.id = 'thumbStripWindow';
      strip.className = 'flex md:flex-col gap-3 shrink-0 w-full md:w-64 h-32 md:h-full overflow-x-auto md:overflow-y-auto p-1';
    }
    this.gridContainer.appendChild(strip);

    cards.forEach((card) => {
      const pid = card.getAttribute('data-participant-id');
      if (pid === this.focusedId) {
        card.classList.add('flex-1', 'h-full', 'border-indigo-500/50', 'shadow-indigo-950/20');
        this.gridContainer.insertBefore(card, strip);
      } else {
        card.classList.add('w-44', 'md:w-full', 'h-full', 'md:h-36', 'shrink-0');
        strip.appendChild(card);
      }
    });
  }

  _applyGridLayout(cards, total) {
    document.getElementById('thumbStripWindow')?.remove();
    cards.forEach((card) => this.gridContainer.appendChild(card));

    this.gridContainer.classList.add('grid');
    if (total === 1) this.gridContainer.classList.add('grid-cols-1', 'max-w-3xl', 'aspect-video');
    else if (total === 2) this.gridContainer.classList.add('grid-cols-1', 'md:grid-cols-2', 'max-w-5xl', 'aspect-video');
    else if (total <= 4) this.gridContainer.classList.add('grid-cols-2', 'h-full');
    else this.gridContainer.classList.add('grid-cols-2', 'md:grid-cols-3', 'h-full');
  }
}