import { OAuth2Client } from 'google-auth-library';

export interface GoogleTokenPayload {
  sub: string; // Google user ID
  email?: string;
  name?: string;
  picture?: string;
  email_verified?: boolean;
}

export interface GoogleTokenVerifier {
  verify(idToken: string): Promise<GoogleTokenPayload>;
}

export class GoogleOAuth2Verifier implements GoogleTokenVerifier {
  private client: OAuth2Client;

  constructor(private clientId: string) {
    this.client = new OAuth2Client(clientId);
  }

  async verify(idToken: string): Promise<GoogleTokenPayload> {
    const ticket = await this.client.verifyIdToken({
      idToken,
      audience: this.clientId,
    });

    const payload = ticket.getPayload();
    if (!payload) {
      throw new Error('No payload in token');
    }

    return {
      sub: payload.sub,
      email: payload.email,
      name: payload.name,
      picture: payload.picture,
      email_verified: payload.email_verified,
    };
  }
}

// Mock verifier for tests
export class MockGoogleTokenVerifier implements GoogleTokenVerifier {
  async verify(idToken: string): Promise<GoogleTokenPayload> {
    if (idToken === 'invalid') {
      throw new Error('Invalid token');
    }

    return {
      sub: 'mock-google-sub-123',
      email: 'test@example.com',
      name: 'Test User',
      picture: 'https://example.com/avatar.jpg',
      email_verified: true,
    };
  }
}
