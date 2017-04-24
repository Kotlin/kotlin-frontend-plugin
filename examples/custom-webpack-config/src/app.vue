<template>
  <div class="main">
    <input type="text" v-model="text" />
    <div class="hello">Hello {{ text }}!</div>
    <button v-on:click="reset">Reset</button>
  </div>
</template>

