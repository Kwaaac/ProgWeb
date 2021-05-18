import * as draw from "./draw.js";

"use strict";

const width = 600;
const height = 600;

function refresh() {
  console.log("tick !");
}

function loaded() {
  window.setTimeout(refresh, 1000);
}

function inter() {
  window.setInterval(function(){
    let date = new Date();
    refresh();
    console.log(date.getHours());
    console.log(date.getMinutes());
    console.log(date.getSeconds());

  }, 1000);
}

function displayDate() {
  let date = new Date();
  document.getElementById("date").innerHTML = date.toDateString();
}

window.onload = inter;

displayDate();

var canvas = document.getElementById('clock');
var context = canvas.getContext("2d");



context.beginPath();
context.moveTo(width/2, 0);
context.lineTo(width/2, height/2);

context.stroke();
