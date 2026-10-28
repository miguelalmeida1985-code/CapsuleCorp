import './lib/compat.js';
import { mount } from 'svelte';
import './inter.css';
import './capsule-fonts.css';
import './app.css';
import App from './App.svelte';

mount(App, { target: document.getElementById('app') });
