#!/usr/bin/env python3
"""
Fetch eBay UK (EBAY_GB) taxonomy/category tree.
Requires: EBAY_CLIENT_ID, EBAY_CLIENT_SECRET environment variables.
Output: scripts/output/ebay/uk/ebay_category_tree.json
"""

import json
import os
import sys
from pathlib import Path
from typing import Optional, Dict, Any
import base64
import urllib.request
import urllib.parse
import urllib.error

def check_credentials() -> tuple[Optional[str], Optional[str]]:
    """Check for eBay API credentials in environment."""
    client_id = os.environ.get("EBAY_CLIENT_ID")
    client_secret = os.environ.get("EBAY_CLIENT_SECRET")

    if not client_id or not client_secret:
        return None, None
    return client_id, client_secret

def get_oauth_token(client_id: str, client_secret: str, sandbox: bool = True) -> Optional[str]:
    """
    Get OAuth 2.0 access token using Client Credentials grant.

    Args:
        client_id: eBay App Client ID
        client_secret: eBay App Client Secret
        sandbox: Use sandbox environment (default True)

    Returns:
        Access token string or None on failure
    """
    # Use sandbox or production endpoint
    auth_url = "https://api.sandbox.ebay.com/identity/v1/oauth2/token" if sandbox else "https://api.ebay.com/identity/v1/oauth2/token"

    # Encode credentials for Basic Auth
    credentials = f"{client_id}:{client_secret}"
    encoded_credentials = base64.b64encode(credentials.encode()).decode()

    # Prepare request
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Authorization": f"Basic {encoded_credentials}"
    }

    data = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "scope": "https://api.ebay.com/oauth/api_scope"
    }).encode()

    try:
        request = urllib.request.Request(auth_url, data=data, headers=headers, method="POST")
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.loads(response.read().decode())
            return result.get("access_token")
    except urllib.error.HTTPError as e:
        error_body = e.read().decode() if e.fp else "No error details"
        print(f"❌ OAuth token request failed: {e.code} {e.reason}", file=sys.stderr)
        print(f"   Error details: {error_body}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"❌ OAuth token request failed: {e}", file=sys.stderr)
        return None

def fetch_ebay_category_tree(access_token: str, marketplace_id: str = "EBAY_GB", sandbox: bool = True) -> Optional[Dict[str, Any]]:
    """
    Fetch eBay category tree using Commerce Taxonomy API.

    Args:
        access_token: OAuth 2.0 access token
        marketplace_id: eBay marketplace code (e.g., EBAY_GB, EBAY_US)
        sandbox: Use sandbox environment (default True)

    Returns:
        Category tree data or None on failure
    """
    # Map marketplace code to category tree ID
    # See: https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getCategoryTree
    marketplace_to_tree_id = {
        "EBAY_US": "0",  # United States
        "EBAY_GB": "3",  # United Kingdom
        "EBAY_DE": "77", # Germany
        "EBAY_FR": "71", # France
        "EBAY_IT": "101", # Italy
        "EBAY_ES": "186", # Spain
        "EBAY_AU": "15",  # Australia
        "EBAY_CA": "2",   # Canada (English)
    }

    category_tree_id = marketplace_to_tree_id.get(marketplace_id, "3")  # Default to UK

    # Use sandbox or production endpoint
    base_url = "https://api.sandbox.ebay.com" if sandbox else "https://api.ebay.com"
    url = f"{base_url}/commerce/taxonomy/v1/category_tree/{category_tree_id}"

    headers = {
        "Authorization": f"Bearer {access_token}",
        "Accept": "application/json"
    }

    try:
        request = urllib.request.Request(url, headers=headers, method="GET")
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as e:
        error_body = e.read().decode() if e.fp else "No error details"
        print(f"❌ Category tree request failed: {e.code} {e.reason}", file=sys.stderr)
        print(f"   Error details: {error_body}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"❌ Category tree request failed: {e}", file=sys.stderr)
        return None

def print_setup_instructions():
    """Print instructions for setting up eBay credentials."""
    print("""
❌ ERROR: eBay API credentials not configured.

To proceed with eBay taxonomy fetching, you need to:

1. Create eBay App Keys:
   - Go to https://developer.ebay.com/
   - Sign in or create a developer account
   - Create an application to get Client ID and Client Secret

2. Set environment variables:
   export EBAY_CLIENT_ID="your_client_id_here"
   export EBAY_CLIENT_SECRET="your_client_secret_here"

   For persistence, add these to your .bashrc, .zshrc, or .env file.

3. Confirm marketplace: EBAY_GB (UK)
   - The script uses EBAY_GB by default
   - To use a different marketplace, set: export EBAY_MARKETPLACE="EBAY_US" (or other)

4. Re-run this script:
   python3 scripts/ebay/fetch_taxonomy.py

Note: This script generates eBay category data for mapping to Scanium subtypes.
It will NOT modify any localization or existing domain pack files.
""")

def fetch_taxonomy_mock() -> Dict[str, Any]:
    """
    Mock implementation that returns a sample category tree structure.
    This demonstrates the expected format while waiting for real API integration.

    In a real implementation, this would call:
    - eBay Auth API to get OAuth token
    - eBay Taxonomy API to fetch category trees
    """
    return {
        "status": "MOCK_DATA",
        "marketplace": "EBAY_GB",
        "message": "Real eBay API integration requires credentials. Using mock structure for demonstration.",
        "categoryTree": {
            "rootId": "0",
            "categoryTreeVersion": "mock_version",
            "categories": [
                {
                    "categoryId": "11232",
                    "categoryName": "Electronics & Computers",
                    "categoryTreeNodeLevel": 1,
                    "parentCategoryId": "0",
                    "subcategories": [
                        {
                            "categoryId": "11232",
                            "categoryName": "Laptops",
                            "categoryTreeNodeLevel": 2,
                            "parentCategoryId": "11232",
                        },
                        {
                            "categoryId": "111",
                            "categoryName": "Monitors",
                            "categoryTreeNodeLevel": 2,
                            "parentCategoryId": "11232",
                        },
                    ]
                },
                {
                    "categoryId": "550",
                    "categoryName": "Home & Garden",
                    "categoryTreeNodeLevel": 1,
                    "parentCategoryId": "0",
                    "subcategories": [
                        {
                            "categoryId": "15687",
                            "categoryName": "Furniture",
                            "categoryTreeNodeLevel": 2,
                            "parentCategoryId": "550",
                        },
                    ]
                },
            ]
        },
        "note": "This is mock data. To fetch real eBay taxonomy, provide valid API credentials."
    }

def fetch_taxonomy() -> int:
    """Fetch eBay UK taxonomy."""
    repo_root = Path(__file__).parent.parent.parent
    output_dir = repo_root / "scripts/output/ebay/uk"
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "ebay_category_tree.json"

    # Check credentials
    client_id, client_secret = check_credentials()

    if not client_id or not client_secret:
        print_setup_instructions()
        # For now, we'll use mock data to allow the workflow to continue
        print("\n⚠️  Using MOCK DATA for demonstration...")
        taxonomy_data = fetch_taxonomy_mock()
        with open(output_path, 'w') as f:
            json.dump(taxonomy_data, f, indent=2)
        print(f"✅ Mock taxonomy tree saved to {output_path}")
        print("   (Replace with real API data once credentials are configured)\n")
        return 0

    print(f"✅ eBay credentials found. Marketplace: EBAY_GB")

    # Detect if using sandbox credentials (contains "SBX")
    is_sandbox = "SBX" in client_id or "sandbox" in client_id.lower()
    env_label = "SANDBOX" if is_sandbox else "PRODUCTION"
    print(f"   Environment: {env_label}")

    # Step 1: Get OAuth token
    print("🔐 Fetching OAuth token...")
    access_token = get_oauth_token(client_id, client_secret, sandbox=is_sandbox)

    if not access_token:
        print("❌ Failed to obtain OAuth token. Falling back to mock data.", file=sys.stderr)
        taxonomy_data = fetch_taxonomy_mock()
        taxonomy_data["status"] = "MOCK_DATA_AUTH_FAILED"
        with open(output_path, 'w') as f:
            json.dump(taxonomy_data, f, indent=2)
        print(f"✅ Mock taxonomy tree saved to {output_path}")
        return 1

    print("✅ OAuth token obtained")

    # Step 2: Fetch category tree
    marketplace_id = os.environ.get("EBAY_MARKETPLACE", "EBAY_GB")
    print(f"📥 Fetching category tree for {marketplace_id}...")

    category_tree = fetch_ebay_category_tree(access_token, marketplace_id, sandbox=is_sandbox)

    if not category_tree:
        print("❌ Failed to fetch category tree. Falling back to mock data.", file=sys.stderr)
        taxonomy_data = fetch_taxonomy_mock()
        taxonomy_data["status"] = "MOCK_DATA_API_FAILED"
        with open(output_path, 'w') as f:
            json.dump(taxonomy_data, f, indent=2)
        print(f"✅ Mock taxonomy tree saved to {output_path}")
        return 1

    # Wrap the response in our expected structure
    taxonomy_data = {
        "status": "REAL_DATA",
        "marketplace": marketplace_id,
        "environment": env_label,
        "categoryTree": category_tree
    }

    with open(output_path, 'w') as f:
        json.dump(taxonomy_data, f, indent=2)

    print(f"✅ Real taxonomy tree saved to {output_path}")

    # Print summary statistics
    root_categories = category_tree.get("rootCategoryNode", {})
    if "childCategoryTreeNodes" in root_categories:
        num_top_level = len(root_categories["childCategoryTreeNodes"])
        print(f"   Top-level categories: {num_top_level}")

    return 0

if __name__ == "__main__":
    sys.exit(fetch_taxonomy())
