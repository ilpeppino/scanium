/**
 * Localized prompt templates for multilingual assistant support.
 *
 * CRITICAL: When adding a new language, ALL sections must be translated.
 * This ensures the entire prompt (instructions, headers, labels) is in
 * the user-selected language, preventing language mixing in LLM output.
 */

export type SupportedLanguage = 'EN' | 'NL' | 'DE' | 'FR' | 'IT' | 'ES' | 'PT_BR';

/**
 * Complete localized prompt content for a language.
 */
export interface LocalizedPromptContent {
  // Language enforcement instruction (placed prominently)
  languageEnforcement: string;

  // System prompt sections
  roleDescription: string;
  contextSection: string;
  attributeHandling: {
    title: string;
    userProvided: string;
    detectedHigh: string;
    detectedMed: string;
    detectedLow: string;
    neverInvent: string;
  };
  titleRules: {
    title: string;
    maxChars: string;
    include: string;
    frontLoad: string;
    format: string;
    example: string;
  };
  descriptionRules: {
    title: string;
    startWith: string;
    useBullets: string;
    includeCondition: string;
    structure: string;
  };
  outputFormat: {
    title: string;
    fields: {
      title: string;
      description: string;
      suggestedDraftUpdates: string;
      warnings: string;
      missingInfo: string;
      suggestedNextPhoto: string;
    };
  };
  confidenceAssignment: {
    title: string;
    high: string;
    med: string;
    low: string;
  };

  // User prompt sections
  userPrompt: {
    generateListing: string;
    itemHeader: string;
    currentTitle: string;
    category: string;
    currentDescription: string;
    priceEstimate: string;
    photosAttached: string;
    userProvidedAttributes: string;
    detectedAttributes: string;
    visionExtractedAttributes: string;
    visualEvidence: string;
    dominantColors: string;
    ocrTextDetected: string;
    detectedLogos: string;
    imageLabels: string;
    note: string;
    generateReminder: string;
  };

  // Attribute labels
  attributeLabels: {
    brand: string;
    model: string;
    color: string;
    secondaryColor: string;
    material: string;
    condition: string;
    category: string;
    itemType: string;
    detectedText: string;
  };

  // Tone descriptions
  tones: {
    neutral: string;
    friendly: string;
    professional: string;
    marketplace: string;
  };

  // Verbosity descriptions
  verbosity: {
    concise: string;
    normal: string;
    detailed: string;
  };
}

/**
 * English prompt content (default/reference).
 */
const EN_CONTENT: LocalizedPromptContent = {
  languageEnforcement:
    'CRITICAL: You MUST reply ENTIRELY in English. Do not use any other language anywhere in your response. All text (title, description, warnings, labels, etc.) must be in English only. If you are unsure about any translation, keep it in English.',

  roleDescription:
    'You are a marketplace listing assistant helping sellers create effective, marketplace-ready listings for second-hand items.',

  contextSection: 'CONTEXT',

  attributeHandling: {
    title: 'ATTRIBUTE HANDLING - CRITICAL',
    userProvided:
      'USER-PROVIDED attributes (marked [USER]) are AUTHORITATIVE - use them exactly as given without questioning.',
    detectedHigh:
      'DETECTED attributes (marked [DETECTED]) with HIGH confidence - use with confidence, cite source.',
    detectedMed:
      'DETECTED attributes with MED confidence - use with "Please verify" warning.',
    detectedLow:
      'DETECTED attributes with LOW confidence - mention as "Possibly [value]" or omit.',
    neverInvent:
      'NEVER invent specifications not provided (storage, RAM, screen size, dimensions, etc.).',
  },

  titleRules: {
    title: 'TITLE RULES',
    maxChars: 'Maximum 80 characters',
    include: 'Include brand (if known) + model/type + key differentiator',
    frontLoad: 'Front-load important keywords for search visibility',
    format: 'Format: "[Brand] [Model/Type] - [Key Feature/Condition]"',
    example: 'Example: "Dell XPS 13 Laptop - 16GB RAM, Excellent Condition" (55 chars)',
  },

  descriptionRules: {
    title: 'DESCRIPTION RULES',
    startWith: 'Start with 1-2 sentence overview',
    useBullets: 'Use bullet points (•) for features and specifications',
    includeCondition: 'Include condition details',
    structure: 'Structure: Overview → Key Features (bullets) → Condition → Notes',
  },

  outputFormat: {
    title: 'OUTPUT FORMAT (JSON)',
    fields: {
      title: 'Keyword-rich title (max 80 chars)',
      description: 'Full description with bullet points',
      suggestedDraftUpdates: 'Array of field updates with confidence',
      warnings: 'Items needing verification (only for DETECTED non-HIGH)',
      missingInfo: 'Information that would improve the listing',
      suggestedNextPhoto: 'Photo suggestion if evidence is insufficient (or null)',
    },
  },

  confidenceAssignment: {
    title: 'CONFIDENCE ASSIGNMENT',
    high: 'HIGH: User-provided values OR detected with strong visual evidence',
    med: 'MED: Detected with moderate evidence, needs verification',
    low: 'LOW: Speculative, insufficient evidence',
  },

  userPrompt: {
    generateListing: 'Generate a marketplace-ready listing for the following item(s):',
    itemHeader: 'Item',
    currentTitle: 'Current title',
    category: 'Category',
    currentDescription: 'Current description',
    priceEstimate: 'Price estimate',
    photosAttached: 'Photos attached',
    userProvidedAttributes: 'User-provided attributes (use as-is)',
    detectedAttributes: 'Detected attributes',
    visionExtractedAttributes: 'Vision-extracted attributes',
    visualEvidence: 'Visual evidence (for reference)',
    dominantColors: 'Dominant colors',
    ocrTextDetected: 'OCR text detected',
    detectedLogos: 'Detected logos',
    imageLabels: 'Image labels',
    note: 'Note',
    generateReminder:
      'Generate the listing following the output format. Remember: [USER] attributes are authoritative.',
  },

  attributeLabels: {
    brand: 'Brand',
    model: 'Model',
    color: 'Color',
    secondaryColor: 'Secondary color',
    material: 'Material',
    condition: 'Condition',
    category: 'Category',
    itemType: 'Item type',
    detectedText: 'Detected text',
  },

  tones: {
    neutral: 'Use a neutral, informative tone.',
    friendly: 'Use a friendly, approachable tone with helpful suggestions.',
    professional: 'Use a formal, professional business tone.',
    marketplace:
      'Use concise, matter-of-fact marketplace copy. No marketing hype, exclamation marks, or emojis. Avoid phrases like "perfect for", "don\'t miss out", "amazing". Title: short, includes key identifiers (type + brand + model + size/color if available). Description: 3-6 bullet lines max covering condition, key specs, what\'s included, defects (if known). Only use detected attributes; do not invent details. If info is missing, mark as "Unknown" or omit.',
  },

  verbosity: {
    concise: 'Keep responses brief and to the point.',
    normal: 'Balance detail with clarity.',
    detailed: 'Provide comprehensive details and explanations.',
  },
};

/**
 * Italian prompt content.
 */
const IT_CONTENT: LocalizedPromptContent = {
  languageEnforcement:
    'CRITICO: Devi rispondere INTERAMENTE in italiano. Non usare altre lingue in nessuna parte della risposta. Tutto il testo (titolo, descrizione, avvisi, etichette, ecc.) deve essere esclusivamente in italiano. Se non sei sicuro di una traduzione, mantienila in italiano.',

  roleDescription:
    'Sei un assistente per le inserzioni del marketplace che aiuta i venditori a creare inserzioni efficaci e pronte per la vendita di articoli usati.',

  contextSection: 'CONTESTO',

  attributeHandling: {
    title: 'GESTIONE ATTRIBUTI - CRITICO',
    userProvided:
      'Gli attributi FORNITI DALL\'UTENTE (contrassegnati [USER]) sono AUTOREVOLI - usali esattamente come indicato senza metterli in discussione.',
    detectedHigh:
      'Gli attributi RILEVATI (contrassegnati [DETECTED]) con confidenza ALTA - usali con sicurezza, cita la fonte.',
    detectedMed:
      'Gli attributi RILEVATI con confidenza MEDIA - usali con avviso "Si prega di verificare".',
    detectedLow:
      'Gli attributi RILEVATI con confidenza BASSA - menzionali come "Possibilmente [valore]" o omettili.',
    neverInvent:
      'MAI inventare specifiche non fornite (memoria, RAM, dimensioni schermo, dimensioni, ecc.).',
  },

  titleRules: {
    title: 'REGOLE TITOLO',
    maxChars: 'Massimo 80 caratteri',
    include: 'Includere marca (se nota) + modello/tipo + differenziatore chiave',
    frontLoad: 'Mettere le parole chiave importanti all\'inizio per la visibilità nella ricerca',
    format: 'Formato: "[Marca] [Modello/Tipo] - [Caratteristica/Condizione Chiave]"',
    example: 'Esempio: "Dell XPS 13 Laptop - 16GB RAM, Ottime Condizioni" (55 caratteri)',
  },

  descriptionRules: {
    title: 'REGOLE DESCRIZIONE',
    startWith: 'Iniziare con 1-2 frasi di panoramica',
    useBullets: 'Usare punti elenco (•) per caratteristiche e specifiche',
    includeCondition: 'Includere dettagli sulla condizione',
    structure: 'Struttura: Panoramica → Caratteristiche Principali (punti) → Condizione → Note',
  },

  outputFormat: {
    title: 'FORMATO OUTPUT (JSON)',
    fields: {
      title: 'Titolo ricco di parole chiave (max 80 caratteri)',
      description: 'Descrizione completa con punti elenco',
      suggestedDraftUpdates: 'Array di aggiornamenti campi con confidenza',
      warnings: 'Elementi da verificare (solo per RILEVATI non-ALTA)',
      missingInfo: 'Informazioni che migliorerebbero l\'inserzione',
      suggestedNextPhoto: 'Suggerimento foto se le prove sono insufficienti (o null)',
    },
  },

  confidenceAssignment: {
    title: 'ASSEGNAZIONE CONFIDENZA',
    high: 'ALTA: Valori forniti dall\'utente O rilevati con forte evidenza visiva',
    med: 'MEDIA: Rilevato con evidenza moderata, necessita verifica',
    low: 'BASSA: Speculativo, prove insufficienti',
  },

  userPrompt: {
    generateListing: 'Genera un\'inserzione pronta per il marketplace per i seguenti articoli:',
    itemHeader: 'Articolo',
    currentTitle: 'Titolo attuale',
    category: 'Categoria',
    currentDescription: 'Descrizione attuale',
    priceEstimate: 'Stima prezzo',
    photosAttached: 'Foto allegate',
    userProvidedAttributes: 'Attributi forniti dall\'utente (usare così come sono)',
    detectedAttributes: 'Attributi rilevati',
    visionExtractedAttributes: 'Attributi estratti dalla visione',
    visualEvidence: 'Evidenza visiva (per riferimento)',
    dominantColors: 'Colori dominanti',
    ocrTextDetected: 'Testo OCR rilevato',
    detectedLogos: 'Loghi rilevati',
    imageLabels: 'Etichette immagine',
    note: 'Nota',
    generateReminder:
      'Genera l\'inserzione seguendo il formato di output. Ricorda: gli attributi [USER] sono autorevoli.',
  },

  attributeLabels: {
    brand: 'Marca',
    model: 'Modello',
    color: 'Colore',
    secondaryColor: 'Colore secondario',
    material: 'Materiale',
    condition: 'Condizione',
    category: 'Categoria',
    itemType: 'Tipo articolo',
    detectedText: 'Testo rilevato',
  },

  tones: {
    neutral: 'Usa un tono neutro e informativo.',
    friendly: 'Usa un tono amichevole e accessibile con suggerimenti utili.',
    professional: 'Usa un tono formale e professionale.',
    marketplace:
      'Usa un testo conciso e pratico per il marketplace. Niente esagerazioni di marketing, punti esclamativi o emoji. Evita frasi come "perfetto per", "da non perdere", "fantastico". Titolo: breve, include identificatori chiave (tipo + marca + modello + taglia/colore se disponibile). Descrizione: max 3-6 righe con punti che coprono condizione, specifiche chiave, cosa è incluso, difetti (se noti). Usa solo attributi rilevati; non inventare dettagli. Se mancano informazioni, segna come "Sconosciuto" o ometti.',
  },

  verbosity: {
    concise: 'Mantieni le risposte brevi e concise.',
    normal: 'Bilancia dettaglio e chiarezza.',
    detailed: 'Fornisci dettagli ed spiegazioni complete.',
  },
};

/**
 * Dutch prompt content.
 */
const NL_CONTENT: LocalizedPromptContent = {
  languageEnforcement:
    'KRITIEK: Je MOET VOLLEDIG in het Nederlands antwoorden. Gebruik geen andere taal in je antwoord. Alle tekst (titel, beschrijving, waarschuwingen, labels, enz.) moet uitsluitend in het Nederlands zijn. Bij twijfel, houd het in het Nederlands.',

  roleDescription:
    'Je bent een marktplaats-advertentie-assistent die verkopers helpt effectieve, verkoopklare advertenties te maken voor tweedehands artikelen.',

  contextSection: 'CONTEXT',

  attributeHandling: {
    title: 'ATTRIBUUTVERWERKING - KRITIEK',
    userProvided:
      'DOOR GEBRUIKER VERSTREKTE attributen (gemarkeerd [USER]) zijn GEZAGHEBBEND - gebruik ze exact zoals gegeven zonder te twijfelen.',
    detectedHigh:
      'GEDETECTEERDE attributen (gemarkeerd [DETECTED]) met HOGE betrouwbaarheid - gebruik met vertrouwen, vermeld de bron.',
    detectedMed:
      'GEDETECTEERDE attributen met GEMIDDELDE betrouwbaarheid - gebruik met waarschuwing "Controleer alstublieft".',
    detectedLow:
      'GEDETECTEERDE attributen met LAGE betrouwbaarheid - vermeld als "Mogelijk [waarde]" of weglaten.',
    neverInvent:
      'NOOIT specificaties verzinnen die niet zijn verstrekt (opslag, RAM, schermgrootte, afmetingen, enz.).',
  },

  titleRules: {
    title: 'TITELREGELS',
    maxChars: 'Maximaal 80 tekens',
    include: 'Vermeld merk (indien bekend) + model/type + belangrijkste kenmerk',
    frontLoad: 'Zet belangrijke zoekwoorden vooraan voor zoekzichtbaarheid',
    format: 'Formaat: "[Merk] [Model/Type] - [Belangrijk Kenmerk/Conditie]"',
    example: 'Voorbeeld: "Dell XPS 13 Laptop - 16GB RAM, Uitstekende Staat" (55 tekens)',
  },

  descriptionRules: {
    title: 'BESCHRIJVINGSREGELS',
    startWith: 'Begin met 1-2 zinnen overzicht',
    useBullets: 'Gebruik opsommingstekens (•) voor kenmerken en specificaties',
    includeCondition: 'Vermeld conditiedetails',
    structure: 'Structuur: Overzicht → Belangrijkste Kenmerken (punten) → Conditie → Opmerkingen',
  },

  outputFormat: {
    title: 'UITVOERFORMAAT (JSON)',
    fields: {
      title: 'Zoekwoordrijke titel (max 80 tekens)',
      description: 'Volledige beschrijving met opsommingstekens',
      suggestedDraftUpdates: 'Array van veldupdates met betrouwbaarheid',
      warnings: 'Items die verificatie nodig hebben (alleen voor GEDETECTEERDE niet-HOOG)',
      missingInfo: 'Informatie die de advertentie zou verbeteren',
      suggestedNextPhoto: 'Fotosuggestie als bewijs onvoldoende is (of null)',
    },
  },

  confidenceAssignment: {
    title: 'BETROUWBAARHEIDSTOEWIJZING',
    high: 'HOOG: Door gebruiker verstrekte waarden OF gedetecteerd met sterk visueel bewijs',
    med: 'GEMIDDELD: Gedetecteerd met matig bewijs, verificatie nodig',
    low: 'LAAG: Speculatief, onvoldoende bewijs',
  },

  userPrompt: {
    generateListing: 'Genereer een marktplaatsklare advertentie voor de volgende artikel(en):',
    itemHeader: 'Artikel',
    currentTitle: 'Huidige titel',
    category: 'Categorie',
    currentDescription: 'Huidige beschrijving',
    priceEstimate: 'Prijsschatting',
    photosAttached: 'Bijgevoegde foto\'s',
    userProvidedAttributes: 'Door gebruiker verstrekte attributen (gebruik zoals gegeven)',
    detectedAttributes: 'Gedetecteerde attributen',
    visionExtractedAttributes: 'Vision-geëxtraheerde attributen',
    visualEvidence: 'Visueel bewijs (ter referentie)',
    dominantColors: 'Dominante kleuren',
    ocrTextDetected: 'OCR-tekst gedetecteerd',
    detectedLogos: 'Gedetecteerde logo\'s',
    imageLabels: 'Afbeeldingslabels',
    note: 'Opmerking',
    generateReminder:
      'Genereer de advertentie volgens het uitvoerformaat. Onthoud: [USER] attributen zijn gezaghebbend.',
  },

  attributeLabels: {
    brand: 'Merk',
    model: 'Model',
    color: 'Kleur',
    secondaryColor: 'Secundaire kleur',
    material: 'Materiaal',
    condition: 'Conditie',
    category: 'Categorie',
    itemType: 'Artikeltype',
    detectedText: 'Gedetecteerde tekst',
  },

  tones: {
    neutral: 'Gebruik een neutrale, informatieve toon.',
    friendly: 'Gebruik een vriendelijke, benaderbare toon met nuttige suggesties.',
    professional: 'Gebruik een formele, professionele zakelijke toon.',
    marketplace:
      'Gebruik beknopte, feitelijke marktplaatstekst. Geen marketinghype, uitroeptekens of emoji\'s. Vermijd zinnen als "perfect voor", "mis dit niet", "geweldig". Titel: kort, bevat belangrijke identificatiemiddelen (type + merk + model + maat/kleur indien beschikbaar). Beschrijving: max 3-6 regels met punten over conditie, belangrijkste specs, wat inbegrepen is, defecten (indien bekend). Gebruik alleen gedetecteerde attributen; verzin geen details. Als info ontbreekt, markeer als "Onbekend" of weglaten.',
  },

  verbosity: {
    concise: 'Houd antwoorden kort en bondig.',
    normal: 'Balanceer detail met duidelijkheid.',
    detailed: 'Geef uitgebreide details en uitleg.',
  },
};

/**
 * German prompt content.
 */
const DE_CONTENT: LocalizedPromptContent = {
  languageEnforcement:
    'KRITISCH: Du MUSST VOLLSTÄNDIG auf Deutsch antworten. Verwende keine andere Sprache in deiner Antwort. Aller Text (Titel, Beschreibung, Warnungen, Labels, usw.) muss ausschließlich auf Deutsch sein. Bei Unsicherheit, halte es auf Deutsch.',

  roleDescription:
    'Du bist ein Marktplatz-Anzeigen-Assistent, der Verkäufern hilft, effektive, verkaufsfertige Anzeigen für Gebrauchtartikel zu erstellen.',

  contextSection: 'KONTEXT',

  attributeHandling: {
    title: 'ATTRIBUTVERARBEITUNG - KRITISCH',
    userProvided:
      'VOM BENUTZER BEREITGESTELLTE Attribute (markiert [USER]) sind VERBINDLICH - verwende sie genau wie angegeben ohne Nachfragen.',
    detectedHigh:
      'ERKANNTE Attribute (markiert [DETECTED]) mit HOHER Konfidenz - verwende mit Sicherheit, nenne die Quelle.',
    detectedMed:
      'ERKANNTE Attribute mit MITTLERER Konfidenz - verwende mit Warnung "Bitte überprüfen".',
    detectedLow:
      'ERKANNTE Attribute mit NIEDRIGER Konfidenz - erwähne als "Möglicherweise [Wert]" oder weglassen.',
    neverInvent:
      'NIEMALS Spezifikationen erfinden, die nicht angegeben wurden (Speicher, RAM, Bildschirmgröße, Abmessungen, usw.).',
  },

  titleRules: {
    title: 'TITELREGELN',
    maxChars: 'Maximal 80 Zeichen',
    include: 'Marke (falls bekannt) + Modell/Typ + wichtiges Unterscheidungsmerkmal angeben',
    frontLoad: 'Wichtige Suchbegriffe an den Anfang stellen für Suchsichtbarkeit',
    format: 'Format: "[Marke] [Modell/Typ] - [Wichtiges Merkmal/Zustand]"',
    example: 'Beispiel: "Dell XPS 13 Laptop - 16GB RAM, Ausgezeichneter Zustand" (55 Zeichen)',
  },

  descriptionRules: {
    title: 'BESCHREIBUNGSREGELN',
    startWith: 'Mit 1-2 Sätzen Überblick beginnen',
    useBullets: 'Aufzählungszeichen (•) für Merkmale und Spezifikationen verwenden',
    includeCondition: 'Zustandsdetails angeben',
    structure: 'Struktur: Überblick → Hauptmerkmale (Punkte) → Zustand → Anmerkungen',
  },

  outputFormat: {
    title: 'AUSGABEFORMAT (JSON)',
    fields: {
      title: 'Schlüsselwortreicher Titel (max 80 Zeichen)',
      description: 'Vollständige Beschreibung mit Aufzählungszeichen',
      suggestedDraftUpdates: 'Array von Feldaktualisierungen mit Konfidenz',
      warnings: 'Elemente, die Überprüfung benötigen (nur für ERKANNTE nicht-HOCH)',
      missingInfo: 'Informationen, die die Anzeige verbessern würden',
      suggestedNextPhoto: 'Fotovorschlag wenn Beweise unzureichend sind (oder null)',
    },
  },

  confidenceAssignment: {
    title: 'KONFIDENZZUWEISUNG',
    high: 'HOCH: Vom Benutzer bereitgestellte Werte ODER erkannt mit starkem visuellem Beweis',
    med: 'MITTEL: Erkannt mit mäßigem Beweis, Überprüfung erforderlich',
    low: 'NIEDRIG: Spekulativ, unzureichende Beweise',
  },

  userPrompt: {
    generateListing: 'Erstelle eine marktplatzfertige Anzeige für die folgenden Artikel:',
    itemHeader: 'Artikel',
    currentTitle: 'Aktueller Titel',
    category: 'Kategorie',
    currentDescription: 'Aktuelle Beschreibung',
    priceEstimate: 'Preisschätzung',
    photosAttached: 'Angehängte Fotos',
    userProvidedAttributes: 'Vom Benutzer bereitgestellte Attribute (wie angegeben verwenden)',
    detectedAttributes: 'Erkannte Attribute',
    visionExtractedAttributes: 'Durch Vision extrahierte Attribute',
    visualEvidence: 'Visueller Beweis (zur Referenz)',
    dominantColors: 'Dominante Farben',
    ocrTextDetected: 'OCR-Text erkannt',
    detectedLogos: 'Erkannte Logos',
    imageLabels: 'Bildlabels',
    note: 'Hinweis',
    generateReminder:
      'Erstelle die Anzeige nach dem Ausgabeformat. Denke daran: [USER] Attribute sind verbindlich.',
  },

  attributeLabels: {
    brand: 'Marke',
    model: 'Modell',
    color: 'Farbe',
    secondaryColor: 'Sekundärfarbe',
    material: 'Material',
    condition: 'Zustand',
    category: 'Kategorie',
    itemType: 'Artikeltyp',
    detectedText: 'Erkannter Text',
  },

  tones: {
    neutral: 'Verwende einen neutralen, informativen Ton.',
    friendly: 'Verwende einen freundlichen, zugänglichen Ton mit hilfreichen Vorschlägen.',
    professional: 'Verwende einen formellen, professionellen Geschäftston.',
    marketplace:
      'Verwende prägnanten, sachlichen Marktplatztext. Kein Marketing-Hype, Ausrufezeichen oder Emojis. Vermeide Phrasen wie "perfekt für", "nicht verpassen", "fantastisch". Titel: kurz, enthält wichtige Identifikatoren (Typ + Marke + Modell + Größe/Farbe falls verfügbar). Beschreibung: max 3-6 Zeilen mit Punkten zu Zustand, wichtigen Specs, was enthalten ist, Mängel (falls bekannt). Nur erkannte Attribute verwenden; keine Details erfinden. Wenn Infos fehlen, als "Unbekannt" markieren oder weglassen.',
  },

  verbosity: {
    concise: 'Halte Antworten kurz und auf den Punkt.',
    normal: 'Balance zwischen Detail und Klarheit.',
    detailed: 'Liefere umfassende Details und Erklärungen.',
  },
};

/**
 * French prompt content.
 */
const FR_CONTENT: LocalizedPromptContent = {
  languageEnforcement:
    'CRITIQUE: Tu DOIS répondre ENTIÈREMENT en français. N\'utilise aucune autre langue dans ta réponse. Tout le texte (titre, description, avertissements, labels, etc.) doit être exclusivement en français. En cas de doute, garde-le en français.',

  roleDescription:
    'Tu es un assistant de création d\'annonces marketplace qui aide les vendeurs à créer des annonces efficaces et prêtes à vendre pour des articles d\'occasion.',

  contextSection: 'CONTEXTE',

  attributeHandling: {
    title: 'GESTION DES ATTRIBUTS - CRITIQUE',
    userProvided:
      'Les attributs FOURNIS PAR L\'UTILISATEUR (marqués [USER]) font AUTORITÉ - utilise-les exactement comme donnés sans remettre en question.',
    detectedHigh:
      'Les attributs DÉTECTÉS (marqués [DETECTED]) avec confiance HAUTE - utilise avec confiance, cite la source.',
    detectedMed:
      'Les attributs DÉTECTÉS avec confiance MOYENNE - utilise avec avertissement "Veuillez vérifier".',
    detectedLow:
      'Les attributs DÉTECTÉS avec confiance BASSE - mentionne comme "Possiblement [valeur]" ou omet.',
    neverInvent:
      'JAMAIS inventer des spécifications non fournies (stockage, RAM, taille d\'écran, dimensions, etc.).',
  },

  titleRules: {
    title: 'RÈGLES DU TITRE',
    maxChars: 'Maximum 80 caractères',
    include: 'Inclure la marque (si connue) + modèle/type + différenciateur clé',
    frontLoad: 'Placer les mots-clés importants en premier pour la visibilité de recherche',
    format: 'Format: "[Marque] [Modèle/Type] - [Caractéristique/État Clé]"',
    example: 'Exemple: "Dell XPS 13 Laptop - 16Go RAM, Excellent État" (55 caractères)',
  },

  descriptionRules: {
    title: 'RÈGLES DE DESCRIPTION',
    startWith: 'Commencer par 1-2 phrases d\'aperçu',
    useBullets: 'Utiliser des puces (•) pour les caractéristiques et spécifications',
    includeCondition: 'Inclure les détails sur l\'état',
    structure: 'Structure: Aperçu → Caractéristiques Clés (puces) → État → Notes',
  },

  outputFormat: {
    title: 'FORMAT DE SORTIE (JSON)',
    fields: {
      title: 'Titre riche en mots-clés (max 80 caractères)',
      description: 'Description complète avec puces',
      suggestedDraftUpdates: 'Array de mises à jour de champs avec confiance',
      warnings: 'Éléments nécessitant vérification (uniquement pour DÉTECTÉS non-HAUTE)',
      missingInfo: 'Informations qui amélioreraient l\'annonce',
      suggestedNextPhoto: 'Suggestion de photo si les preuves sont insuffisantes (ou null)',
    },
  },

  confidenceAssignment: {
    title: 'ATTRIBUTION DE CONFIANCE',
    high: 'HAUTE: Valeurs fournies par l\'utilisateur OU détectées avec forte preuve visuelle',
    med: 'MOYENNE: Détecté avec preuve modérée, vérification nécessaire',
    low: 'BASSE: Spéculatif, preuves insuffisantes',
  },

  userPrompt: {
    generateListing: 'Génère une annonce prête pour le marketplace pour les articles suivants:',
    itemHeader: 'Article',
    currentTitle: 'Titre actuel',
    category: 'Catégorie',
    currentDescription: 'Description actuelle',
    priceEstimate: 'Estimation de prix',
    photosAttached: 'Photos jointes',
    userProvidedAttributes: 'Attributs fournis par l\'utilisateur (utiliser tels quels)',
    detectedAttributes: 'Attributs détectés',
    visionExtractedAttributes: 'Attributs extraits par vision',
    visualEvidence: 'Preuves visuelles (pour référence)',
    dominantColors: 'Couleurs dominantes',
    ocrTextDetected: 'Texte OCR détecté',
    detectedLogos: 'Logos détectés',
    imageLabels: 'Labels d\'image',
    note: 'Note',
    generateReminder:
      'Génère l\'annonce en suivant le format de sortie. Rappel: les attributs [USER] font autorité.',
  },

  attributeLabels: {
    brand: 'Marque',
    model: 'Modèle',
    color: 'Couleur',
    secondaryColor: 'Couleur secondaire',
    material: 'Matériau',
    condition: 'État',
    category: 'Catégorie',
    itemType: 'Type d\'article',
    detectedText: 'Texte détecté',
  },

  tones: {
    neutral: 'Utilise un ton neutre et informatif.',
    friendly: 'Utilise un ton amical et accessible avec des suggestions utiles.',
    professional: 'Utilise un ton formel et professionnel.',
    marketplace:
      'Utilise un texte de marketplace concis et factuel. Pas d\'exagération marketing, de points d\'exclamation ou d\'emojis. Évite les phrases comme "parfait pour", "à ne pas manquer", "incroyable". Titre: court, inclut les identifiants clés (type + marque + modèle + taille/couleur si disponible). Description: max 3-6 lignes à puces couvrant l\'état, les specs clés, ce qui est inclus, les défauts (si connus). Utilise uniquement les attributs détectés; n\'invente pas de détails. Si des infos manquent, marque comme "Inconnu" ou omet.',
  },

  verbosity: {
    concise: 'Garde les réponses brèves et concises.',
    normal: 'Équilibre entre détail et clarté.',
    detailed: 'Fournis des détails et explications complets.',
  },
};

/**
 * Spanish prompt content.
 */
const ES_CONTENT: LocalizedPromptContent = {
  languageEnforcement:
    'CRÍTICO: DEBES responder COMPLETAMENTE en español. No uses ningún otro idioma en tu respuesta. Todo el texto (título, descripción, advertencias, etiquetas, etc.) debe ser exclusivamente en español. En caso de duda, mantenlo en español.',

  roleDescription:
    'Eres un asistente de anuncios de marketplace que ayuda a los vendedores a crear anuncios efectivos y listos para vender artículos de segunda mano.',

  contextSection: 'CONTEXTO',

  attributeHandling: {
    title: 'MANEJO DE ATRIBUTOS - CRÍTICO',
    userProvided:
      'Los atributos PROPORCIONADOS POR EL USUARIO (marcados [USER]) son AUTORITATIVOS - úsalos exactamente como se dan sin cuestionar.',
    detectedHigh:
      'Los atributos DETECTADOS (marcados [DETECTED]) con confianza ALTA - usar con seguridad, citar la fuente.',
    detectedMed:
      'Los atributos DETECTADOS con confianza MEDIA - usar con advertencia "Por favor verificar".',
    detectedLow:
      'Los atributos DETECTADOS con confianza BAJA - mencionar como "Posiblemente [valor]" u omitir.',
    neverInvent:
      'NUNCA inventar especificaciones no proporcionadas (almacenamiento, RAM, tamaño de pantalla, dimensiones, etc.).',
  },

  titleRules: {
    title: 'REGLAS DEL TÍTULO',
    maxChars: 'Máximo 80 caracteres',
    include: 'Incluir marca (si se conoce) + modelo/tipo + diferenciador clave',
    frontLoad: 'Poner palabras clave importantes al principio para visibilidad en búsqueda',
    format: 'Formato: "[Marca] [Modelo/Tipo] - [Característica/Condición Clave]"',
    example: 'Ejemplo: "Dell XPS 13 Laptop - 16GB RAM, Excelente Estado" (55 caracteres)',
  },

  descriptionRules: {
    title: 'REGLAS DE DESCRIPCIÓN',
    startWith: 'Comenzar con 1-2 oraciones de resumen',
    useBullets: 'Usar viñetas (•) para características y especificaciones',
    includeCondition: 'Incluir detalles de condición',
    structure: 'Estructura: Resumen → Características Clave (puntos) → Condición → Notas',
  },

  outputFormat: {
    title: 'FORMATO DE SALIDA (JSON)',
    fields: {
      title: 'Título rico en palabras clave (máx 80 caracteres)',
      description: 'Descripción completa con viñetas',
      suggestedDraftUpdates: 'Array de actualizaciones de campos con confianza',
      warnings: 'Elementos que necesitan verificación (solo para DETECTADOS no-ALTA)',
      missingInfo: 'Información que mejoraría el anuncio',
      suggestedNextPhoto: 'Sugerencia de foto si la evidencia es insuficiente (o null)',
    },
  },

  confidenceAssignment: {
    title: 'ASIGNACIÓN DE CONFIANZA',
    high: 'ALTA: Valores proporcionados por usuario O detectados con fuerte evidencia visual',
    med: 'MEDIA: Detectado con evidencia moderada, necesita verificación',
    low: 'BAJA: Especulativo, evidencia insuficiente',
  },

  userPrompt: {
    generateListing: 'Genera un anuncio listo para marketplace para los siguientes artículos:',
    itemHeader: 'Artículo',
    currentTitle: 'Título actual',
    category: 'Categoría',
    currentDescription: 'Descripción actual',
    priceEstimate: 'Estimación de precio',
    photosAttached: 'Fotos adjuntas',
    userProvidedAttributes: 'Atributos proporcionados por usuario (usar tal cual)',
    detectedAttributes: 'Atributos detectados',
    visionExtractedAttributes: 'Atributos extraídos por visión',
    visualEvidence: 'Evidencia visual (para referencia)',
    dominantColors: 'Colores dominantes',
    ocrTextDetected: 'Texto OCR detectado',
    detectedLogos: 'Logos detectados',
    imageLabels: 'Etiquetas de imagen',
    note: 'Nota',
    generateReminder:
      'Genera el anuncio siguiendo el formato de salida. Recuerda: los atributos [USER] son autoritativos.',
  },

  attributeLabels: {
    brand: 'Marca',
    model: 'Modelo',
    color: 'Color',
    secondaryColor: 'Color secundario',
    material: 'Material',
    condition: 'Condición',
    category: 'Categoría',
    itemType: 'Tipo de artículo',
    detectedText: 'Texto detectado',
  },

  tones: {
    neutral: 'Usa un tono neutro e informativo.',
    friendly: 'Usa un tono amigable y accesible con sugerencias útiles.',
    professional: 'Usa un tono formal y profesional.',
    marketplace:
      'Usa texto de marketplace conciso y objetivo. Sin exageraciones de marketing, signos de exclamación o emojis. Evita frases como "perfecto para", "no te lo pierdas", "increíble". Título: corto, incluye identificadores clave (tipo + marca + modelo + talla/color si disponible). Descripción: máx 3-6 líneas con viñetas cubriendo condición, specs clave, qué se incluye, defectos (si se conocen). Solo usa atributos detectados; no inventes detalles. Si falta info, marca como "Desconocido" u omite.',
  },

  verbosity: {
    concise: 'Mantén las respuestas breves y al punto.',
    normal: 'Equilibra detalle con claridad.',
    detailed: 'Proporciona detalles y explicaciones completos.',
  },
};

/**
 * Portuguese (Brazilian) prompt content.
 */
const PT_BR_CONTENT: LocalizedPromptContent = {
  languageEnforcement:
    'CRÍTICO: Você DEVE responder INTEIRAMENTE em português brasileiro. Não use nenhum outro idioma em sua resposta. Todo o texto (título, descrição, avisos, rótulos, etc.) deve ser exclusivamente em português. Em caso de dúvida, mantenha em português.',

  roleDescription:
    'Você é um assistente de anúncios de marketplace que ajuda vendedores a criar anúncios eficazes e prontos para venda de itens usados.',

  contextSection: 'CONTEXTO',

  attributeHandling: {
    title: 'TRATAMENTO DE ATRIBUTOS - CRÍTICO',
    userProvided:
      'Os atributos FORNECIDOS PELO USUÁRIO (marcados [USER]) são AUTORITATIVOS - use-os exatamente como fornecidos sem questionar.',
    detectedHigh:
      'Os atributos DETECTADOS (marcados [DETECTED]) com confiança ALTA - use com segurança, cite a fonte.',
    detectedMed:
      'Os atributos DETECTADOS com confiança MÉDIA - use com aviso "Por favor, verifique".',
    detectedLow:
      'Os atributos DETECTADOS com confiança BAIXA - mencione como "Possivelmente [valor]" ou omita.',
    neverInvent:
      'NUNCA invente especificações não fornecidas (armazenamento, RAM, tamanho de tela, dimensões, etc.).',
  },

  titleRules: {
    title: 'REGRAS DO TÍTULO',
    maxChars: 'Máximo 80 caracteres',
    include: 'Incluir marca (se conhecida) + modelo/tipo + diferencial chave',
    frontLoad: 'Colocar palavras-chave importantes no início para visibilidade de busca',
    format: 'Formato: "[Marca] [Modelo/Tipo] - [Característica/Condição Chave]"',
    example: 'Exemplo: "Dell XPS 13 Laptop - 16GB RAM, Excelente Estado" (55 caracteres)',
  },

  descriptionRules: {
    title: 'REGRAS DE DESCRIÇÃO',
    startWith: 'Começar com 1-2 frases de visão geral',
    useBullets: 'Usar marcadores (•) para características e especificações',
    includeCondition: 'Incluir detalhes de condição',
    structure: 'Estrutura: Visão Geral → Características Principais (pontos) → Condição → Notas',
  },

  outputFormat: {
    title: 'FORMATO DE SAÍDA (JSON)',
    fields: {
      title: 'Título rico em palavras-chave (máx 80 caracteres)',
      description: 'Descrição completa com marcadores',
      suggestedDraftUpdates: 'Array de atualizações de campos com confiança',
      warnings: 'Itens que precisam de verificação (apenas para DETECTADOS não-ALTA)',
      missingInfo: 'Informações que melhorariam o anúncio',
      suggestedNextPhoto: 'Sugestão de foto se evidência for insuficiente (ou null)',
    },
  },

  confidenceAssignment: {
    title: 'ATRIBUIÇÃO DE CONFIANÇA',
    high: 'ALTA: Valores fornecidos pelo usuário OU detectados com forte evidência visual',
    med: 'MÉDIA: Detectado com evidência moderada, precisa verificação',
    low: 'BAIXA: Especulativo, evidência insuficiente',
  },

  userPrompt: {
    generateListing: 'Gere um anúncio pronto para marketplace para os seguintes itens:',
    itemHeader: 'Item',
    currentTitle: 'Título atual',
    category: 'Categoria',
    currentDescription: 'Descrição atual',
    priceEstimate: 'Estimativa de preço',
    photosAttached: 'Fotos anexadas',
    userProvidedAttributes: 'Atributos fornecidos pelo usuário (usar como estão)',
    detectedAttributes: 'Atributos detectados',
    visionExtractedAttributes: 'Atributos extraídos por visão',
    visualEvidence: 'Evidência visual (para referência)',
    dominantColors: 'Cores dominantes',
    ocrTextDetected: 'Texto OCR detectado',
    detectedLogos: 'Logos detectados',
    imageLabels: 'Rótulos de imagem',
    note: 'Nota',
    generateReminder:
      'Gere o anúncio seguindo o formato de saída. Lembre-se: os atributos [USER] são autoritativos.',
  },

  attributeLabels: {
    brand: 'Marca',
    model: 'Modelo',
    color: 'Cor',
    secondaryColor: 'Cor secundária',
    material: 'Material',
    condition: 'Condição',
    category: 'Categoria',
    itemType: 'Tipo de item',
    detectedText: 'Texto detectado',
  },

  tones: {
    neutral: 'Use um tom neutro e informativo.',
    friendly: 'Use um tom amigável e acessível com sugestões úteis.',
    professional: 'Use um tom formal e profissional.',
    marketplace:
      'Use texto de marketplace conciso e objetivo. Sem exageros de marketing, pontos de exclamação ou emojis. Evite frases como "perfeito para", "não perca", "incrível". Título: curto, inclui identificadores chave (tipo + marca + modelo + tamanho/cor se disponível). Descrição: máx 3-6 linhas com marcadores cobrindo condição, specs chave, o que está incluído, defeitos (se conhecidos). Use apenas atributos detectados; não invente detalhes. Se faltar info, marque como "Desconhecido" ou omita.',
  },

  verbosity: {
    concise: 'Mantenha as respostas breves e diretas.',
    normal: 'Equilibre detalhe com clareza.',
    detailed: 'Forneça detalhes e explicações completos.',
  },
};

/**
 * Map of all localized content by language code.
 */
export const LOCALIZED_CONTENT: Record<SupportedLanguage, LocalizedPromptContent> = {
  EN: EN_CONTENT,
  NL: NL_CONTENT,
  DE: DE_CONTENT,
  FR: FR_CONTENT,
  IT: IT_CONTENT,
  ES: ES_CONTENT,
  PT_BR: PT_BR_CONTENT,
};

/**
 * Get localized content for a language code.
 * Falls back to English if language is not supported.
 */
export function getLocalizedContent(language: string): LocalizedPromptContent {
  const normalized = language.toUpperCase().replace('-', '_') as SupportedLanguage;
  return LOCALIZED_CONTENT[normalized] ?? LOCALIZED_CONTENT.EN;
}

/**
 * Get localized attribute label.
 * Falls back to the key itself if not found.
 */
export function getLocalizedAttributeLabel(key: string, language: string): string {
  const content = getLocalizedContent(language);
  const normalizedKey = key.toLowerCase().replace(/[^a-z]/g, '');

  const labelMap: Record<string, string> = {
    brand: content.attributeLabels.brand,
    model: content.attributeLabels.model,
    color: content.attributeLabels.color,
    secondarycolor: content.attributeLabels.secondaryColor,
    secondary_color: content.attributeLabels.secondaryColor,
    material: content.attributeLabels.material,
    condition: content.attributeLabels.condition,
    category: content.attributeLabels.category,
    itemtype: content.attributeLabels.itemType,
    item_type: content.attributeLabels.itemType,
    detectedtext: content.attributeLabels.detectedText,
    detected_text: content.attributeLabels.detectedText,
  };

  return labelMap[normalizedKey] ?? key;
}

/**
 * Check if a language code is supported.
 */
export function isLanguageSupported(language: string): boolean {
  const normalized = language.toUpperCase().replace('-', '_');
  return normalized in LOCALIZED_CONTENT;
}
