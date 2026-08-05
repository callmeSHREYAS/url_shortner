async function createUrl() {

    const name = document.getElementById("name").value;
    const url = document.getElementById("url").value;

    if (!name || !url) {
        alert("Fill all fields");
        return;
    }

    await fetch("/api/v1/url", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            name: name,
            url: url
        })
    });

    document.getElementById("name").value = "";
    document.getElementById("url").value = "";

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

    const urls = await response.json();

    const container = document.getElementById("url-list");
    const template = document.getElementById("url-card-template");

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
