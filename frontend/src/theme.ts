import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    primary: { main: '#1565c0' },
    background: { default: '#f5f7fb' },
  },
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: '"Inter", "Roboto", "Segoe UI", system-ui, -apple-system, "Helvetica Neue", Arial, sans-serif',
    h5: { fontWeight: 600 },
  },
  components: {
    MuiPaper: { defaultProps: { variant: 'outlined' } },
    MuiTableCell: { styleOverrides: { head: { fontWeight: 600, whiteSpace: 'nowrap' } } },
  },
});
