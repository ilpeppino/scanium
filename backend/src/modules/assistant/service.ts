import { AssistantProvider } from './provider.js';
import { AssistantChatRequest, AssistantResponse } from './types.js';
import { refusalResponse, sanitizeActions, shouldRefuse } from './safety.js';

export class AssistantService {
  constructor(private readonly provider: AssistantProvider) {}

  async respond(request: AssistantChatRequest): Promise<AssistantResponse> {
    if (shouldRefuse(request.message || '')) {
      return refusalResponse();
    }

    const response = await this.provider.respond(request);
    return {
      content: response.content,
      actions: sanitizeActions(response.actions),
      citationsMetadata: response.citationsMetadata,
    };
  }
}
