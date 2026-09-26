import nbformat
import sys

def py_to_ipynb(py_file, ipynb_file):
    with open(py_file, 'r', encoding='utf-8') as f:
        content = f.read()

    nb = nbformat.v4.new_notebook()
    cells = content.split('# %%')
    
    for cell in cells:
        cell = cell.strip()
        if not cell:
            continue
        
        if cell.startswith('[markdown]'):
            lines = cell.split('\n')[1:]
            markdown_content = '\n'.join([line.replace('# ', '', 1) if line.startswith('# ') else line for line in lines])
            nb.cells.append(nbformat.v4.new_markdown_cell(markdown_content))
        else:
            nb.cells.append(nbformat.v4.new_code_cell(cell))

    with open(ipynb_file, 'w', encoding='utf-8') as f:
        nbformat.write(nb, f)

if __name__ == '__main__':
    py_to_ipynb('dehradun_route_dataset_generator.py', 'Suraksha_Setu_Dataset_Generator.ipynb')
