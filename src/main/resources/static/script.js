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

async function loadUrls() {

    const response = await fetch("api/v1/url");

    const urls = await response.json();

    const container = document.getElementById("url-list");

    container.innerHTML = "";

    urls.forEach(url => {

        container.innerHTML += `
            <div class="card">
                <h3>${url.name}</h3>

                <p>
                    Original URL:
                    ${url.url}
                </p>

                <a href="/api/v1/url/${url.shortCode}" target="_blank">
                    localhost:8080/api/v1/url/${url.shortCode }
                </a>
            </div>
        `;
    });
}

window.onload = loadUrls;