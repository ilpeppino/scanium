// Replace this URL once the Scanium Google Play listing is live.
const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=YOUR_APP_ID";

const playStoreLinks = document.querySelectorAll("[data-play-store-link]");
playStoreLinks.forEach((link) => {
  link.setAttribute("href", PLAY_STORE_URL);
  link.setAttribute("rel", "noopener");
  link.setAttribute("target", "_blank");
});
