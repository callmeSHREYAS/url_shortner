async function createUrl() {
    const nameInput = document.getElementById("name");
    const urlInput = document.getElementById("url");
    const name = nameInput.value.trim();
    const url = urlInput.value.trim();

    if (!name || !url) {
        alert("Fill all fields");
        return;
    }

    const response = await fetch("/api/v1/url", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            name: name,
            url: url
        })
    });

    if (!response.ok) {
        alert("URL was not created");
        return;
    }

    nameInput.value = "";
    urlInput.value = "";

    loadUrls();
}

async function deleteUrl(id) {
    const response = await fetch(`/api/v1/url/delete/${id}`, {
        method: "DELETE"
    });

    if (!response.ok) {
        alert("URL was not deleted");
        return;
    }

    loadUrls();
}

async function loadUrls() {

    const response = await fetch("/api/v1/url");

    if (!response.ok) {
        alert("URLs could not be loaded");
        return;
    }

    const urls = await response.json();

    const container = document.getElementById("url-list");
    const template = document.getElementById("url-card-template");

    if (!container || !template) {
        console.error("Missing url-list container or url-card-template in index.html");
        return;
    }

    container.innerHTML = "";

    urls.forEach(url => {
        const card = template.content.cloneNode(true);
        const shortUrl = `${window.location.origin}/api/v1/url/${url.shortCode}`;

        card.querySelector(".url-name").textContent = url.name;
        card.querySelector(".original-url").textContent = url.url;
        card.querySelector(".total-clicks").textContent = url.tot_Clicks || 0;

        const shortUrlLink = card.querySelector(".short-url");
        shortUrlLink.href = `/api/v1/url/${url.shortCode}`;
        shortUrlLink.textContent = shortUrl;

        card.querySelector(".delete-btn").addEventListener("click", () => deleteUrl(url.id));

        container.appendChild(card);
    });
}

window.onload = loadUrls;
