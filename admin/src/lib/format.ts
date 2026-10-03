import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";

dayjs.extend(relativeTime);

const rupees = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });
const rupeesShort = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

export const formatINR = (amount: number) => rupees.format(amount);
export const formatINRShort = (amount: number) => rupeesShort.format(amount);

/** "3 Oct 2026, 9:51 pm", or a dash when the time is unknown. */
export const formatDateTime = (ms: number) => (ms > 0 ? dayjs(ms).format("D MMM YYYY, h:mm a") : "—");
export const formatDate = (ms: number) => (ms > 0 ? dayjs(ms).format("D MMM YYYY") : "—");

/** "5 minutes ago". */
export const fromNow = (ms: number) => (ms > 0 ? dayjs(ms).fromNow() : "—");

/** Hides most of a push token; it is a delivery address, not something to display. */
export const maskToken = (token: string) => (token.length > 16 ? `${token.slice(0, 8)}…${token.slice(-6)}` : token);
