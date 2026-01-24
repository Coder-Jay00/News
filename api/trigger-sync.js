
/**
 * Vercel Serverless Function: Trigger GitHub Action News Sync
 * This proxy allows the mobile app to trigger a manual news crawl
 */
export default async function handler(req, res) {
    // 1. Security Check (Basic - You can add an API Key later)
    if (req.method !== 'POST') {
        return res.status(405).json({ error: "Method Not Allowed" });
    }

    const GITHUB_TOKEN = process.env.GITHUB_TOKEN; // Set this in Vercel Dashboard
    const REPO_OWNER = "Coder-Jay00";
    const REPO_NAME = "News";
    const WORKFLOW_ID = "daily_brief_pipeline.yml";

    if (!GITHUB_TOKEN) {
        return res.status(500).json({ error: "Cloud Configuration Missing (GITHUB_TOKEN)" });
    }

    try {
        console.log(`[Relay] Triggering sync for ${REPO_OWNER}/${REPO_NAME}...`);

        const response = await fetch(
            `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/actions/workflows/${WORKFLOW_ID}/dispatches`,
            {
                method: "POST",
                headers: {
                    "Authorization": `Bearer ${GITHUB_TOKEN}`,
                    "Accept": "application/vnd.github.v3+json",
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    ref: "main", // Trigger the main branch
                }),
            }
        );

        if (response.status === 204) {
            return res.status(200).json({ success: true, message: "Intelligence Sync Started" });
        } else {
            const errorData = await response.json();
            return res.status(response.status).json({ success: false, error: errorData });
        }
    } catch (err) {
        return res.status(500).json({ success: false, error: err.message });
    }
}
