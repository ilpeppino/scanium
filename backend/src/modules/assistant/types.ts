export type AssistantRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export type AssistantMessage = {
  role: AssistantRole;
  content: string;
  timestamp: number;
  itemContextIds?: string[];
};

export type AssistantActionType =
  | 'APPLY_DRAFT_UPDATE'
  | 'COPY_TEXT'
  | 'OPEN_POSTING_ASSIST'
  | 'OPEN_SHARE'
  | 'OPEN_URL';

export type AssistantAction = {
  type: AssistantActionType;
  payload?: Record<string, string>;
};

export type AssistantResponse = {
  content: string;
  actions?: AssistantAction[];
  citationsMetadata?: Record<string, string>;
};

export type ItemAttributeSnapshot = {
  key: string;
  value: string;
  confidence?: number;
};

export type ItemContextSnapshot = {
  itemId: string;
  title?: string | null;
  category?: string | null;
  confidence?: number | null;
  attributes?: ItemAttributeSnapshot[];
  priceEstimate?: number | null;
  photosCount?: number;
  exportProfileId?: string;
};

export type ExportProfileSnapshot = {
  id: string;
  displayName: string;
};

export type AssistantChatRequest = {
  items: ItemContextSnapshot[];
  history?: AssistantMessage[];
  message: string;
  exportProfile?: ExportProfileSnapshot;
};
