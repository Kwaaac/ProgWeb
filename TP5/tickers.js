"use strict";

function createRow(ticker, stockTable) {
    let emptyTD;
    let tr = document.createElement("tr");
    let title = document.createElement("td");
    title.classList.add("title");
    title.innerText = ticker;

    tr.appendChild(title);

    for (let i = 0; i < 10; i++) {
        emptyTD = document.createElement("td");
        emptyTD.innerText = "-";

        tr.appendChild(emptyTD);
    }

    stockTable.appendChild(tr);
}

function jsonOkOrError(response) {
    if (response.ok) {
        return response.json();
    }
    throw Error(response.statusText);
}

function getFirstQuotationIndex(stockTable) {
    for (let i = 0; i < stockTable.children[0].children.length; i++) {
        if (stockTable.children[0].children[i].innerHTML === "-") {
            return i;
        }
    }
    return -1;
}

function markGreenText(value, tableStock) {
    Array.from(tableStock.children).forEach(function (row) {
        Array.from(row.children).forEach(function (stock) {
            if (stock.innerText !== "-") {
                if (parseInt(stock.innerText) < value) {
                    stock.classList.add("less");
                } else {
                    stock.classList.remove("less");
                }
            }
        })
    })
}

function fetchJsonQuotations(tab_t, stockTable, value) {
    fetch('/api/stock', {method: "post"})
        .then(jsonOkOrError)
        .then(stocks => {
            let stockIndex = getFirstQuotationIndex(stockTable);
            if (stockIndex === -1) {
                console.log("Les dix quotations ont été récupérés");
                return;
            }

            tab_t.forEach(function (ticker, i) {
                stockTable.children[i].children[stockIndex].innerHTML = stocks[ticker];
            })

            markGreenText(value, stockTable);
        })
        .catch(err => console.log(err.message));
}

window.onload = () => {
    let refreshButton = document.getElementById("refreshButton");
    let stockTable = document.getElementById("stockTable");
    let markText = document.getElementById("markText");
    let timeoutTab = [];

    markText.onkeyup = () => {
        markGreenText(parseInt(markText.value), stockTable);
    }

    refreshButton.onclick = () => {
        timeoutTab.forEach(timeout => {
            clearTimeout(timeout);
        })
        stockTable.innerHTML = "";
        fetch('/api/ticker')
            .then(jsonOkOrError)
            .then(tickers => {
                tickers.forEach(function (ticker) {
                    createRow(ticker, stockTable);
                })

                for (let i = 1; i < stockTable.children[0].children.length; i++) {
                    timeoutTab.push(window.setTimeout(fetchJsonQuotations, 1000 * i, tickers, stockTable, parseInt(markText.value)));
                }

            })
            .catch(err => console.log(err.message));
    };


};